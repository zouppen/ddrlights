// -*- mode: c++; c-file-style: "linux" -*-
#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>

// Customizable application specific values
#define LED_PIN      13 /* Informative LED */
#define LIGHTS_START 2  /* Lowest pin of lights */
#define LIGHTS_LEN   6  /* Number of LEDS to control */

// Command types
const byte not_command        = 0x00;
const byte cmd_begin_write    = 0x01;
const byte cmd_refresh        = 0x02;

// Other return values from uncons()
const int is_timeout = -1;
const int is_payload = not_command;

// Some protocol specific constants and helpers
const byte escape = 0x7e;
const unsigned int timeout_naks = 15000; // About a second

// Globals
byte lights[LIGHTS_LEN];

AndroidAccessory acc("Koodilehto",
		     "DDRController",
		     "DDR Light belt controller",
		     "0.1",
		     "http://www.koodilehto.fi/projects/ddr",
		     "0000000012345678");
    
void setup()
{
	// Setup LED and serial for debugging
	Serial.begin(115200);
	pinMode(LED_PIN, OUTPUT);

	// Set PWM outputs active
	for (int i=0; i<LIGHTS_LEN; i++) {
		pinMode(LIGHTS_START+i, OUTPUT);
		lights[i] = 0; // Dark as default.
	}

	// Start in accessory mode
	acc.powerOn();
}

void loop()
{
	// In the beginning we are in idle state
	switch (uncons(NULL)) {
	case cmd_begin_write:
		read_array();
		break;
	case cmd_refresh:
		hardware_write();
		break;
	}
	// We have no default case because if a payload byte or
	// invalid command is received, we are throwing it away and go
	// on until we have a command we can handle.
}

/**
 * Reads an array from the USB. Fails silently. If that's an issue,
 * you should fix it. Also, this may run uncons in vain after errors,
 * but who really cares if it is in error state anyway.
 */
void read_array(void) {
	byte start, len;
	
	uncons(&start);
	uncons(&len);

	// If trying to send too much, exit.
	if (start+len > LIGHTS_LEN) return;

	for (int i=start; i<len; i++) {
		uncons(lights+i);
	}

	// Debug
	Serial.print("write complete. buffer after: ");
	for (int i=0; i<LIGHTS_LEN; i++) Serial.print(lights[i],HEX);
	Serial.println("");
}

/**
 * Refreshes current buffer to the hardware.
 */
void hardware_write(void) {
	for (int i=0; i<LIGHTS_LEN; i++) {
		analogWrite(LIGHTS_START+i,lights[i]);
	}
	Serial.println("refresh complete");
}


/**
 * Reads a command or character from Android. This function has two
 * operating modes. Returns command character, or not_command, if
 * payload byte is received instead.
 * 
 * Case A: When pos is NULL, nothing is written to actual buffer and
 * receiving commands is allowed. If payload byte is received, it is
 * ignored.
 *
 * Case B: When pos is not NULL, a payload byte is expected and
 * written to the address of given pointer. If a command is received
 * instead, it returns without consuming anything.
 */
int uncons(byte *pos) {
	// TODO use ring buffer for better performance. Until we have
	// a bottleneck in here, we are doing it dummy way, byte by
	// byte.

	static byte unget_command = not_command;
	byte value;

	// Checks if there are ungetted commands
	if (unget_command != not_command) {
		Serial.println("tuli kakusta");
		byte tmp = unget_command;
		// Cleaning command status caller consumes commands.
		if (pos == NULL) unget_command = not_command;
		return tmp;
	}

	// Do the read
	if (acc.read(&value, 1, timeout_naks) != 1) return is_timeout;
	Serial.println("luettiin");

	if (value == escape) {
		// Reads next byte after escape
		if (acc.read(&value, 1, timeout_naks) != 1) return is_timeout;

		// If it is a command
		if (value != not_command) {
			// If the caller expects payload, unget command.
			if (pos != NULL) unget_command = value;
			return value;
		}
		// Else, it's a literal escape.
		value = escape;
	}
	// Writing to address only if a buffer is given. Otherwise
	// output is ignored.
	if (pos != NULL) *pos = value;
	return is_payload;
}
