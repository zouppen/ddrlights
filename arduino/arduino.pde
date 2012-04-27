// -*- mode: c++; c-file-style: "linux" -*-
#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>

// Customizable application specific values
#define BUFFER_SIZE 128 /* Maximum receive size */
const byte belt_leds[] = {2,3,4,5,6,9}; // Pin 7 is used with MAX3421E

// Command types
const byte not_command        = 0x00;
const byte cmd_begin_write    = 0x01;
const byte cmd_refresh        = 0x02;

// Some protocol specific constants and helpers
const byte escape = 0x7e;
const unsigned int timeout_naks = 0xffff; // Maximum timeout length available.
const size_t belt_len = sizeof(belt_leds)/sizeof(byte);

// Defining structure of shared memory. It is not packed. You may
// modify it to use packed struct, if you are have alignment problems.
struct
{
	// Currently it has only the belt lights.
	byte belt[belt_len];

} shared;

// Information for Android device follows
AndroidAccessory acc("Koodilehto",
		     "DDRController",
		     "DDR Light belt controller",
		     "0.1",
		     "http://www.koodilehto.fi/projects/ddr",
		     "0000000012345678");
    
void setup()
{
	// Setup serial port for debugging
	Serial.begin(115200);

	// Set PWM outputs active and clean shared memory
	for (int i=0; i<belt_len; i++) {
		pinMode(belt_leds[i], OUTPUT);
		shared.belt[i] = 0; // Dark as default.
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
 * Reads an array from the USB to the shared memory. Fails
 * silently. If that's an issue, you should fix it. Also, this may run
 * uncons in vain after errors, but who really cares if it is in error
 * state anyway. This is intentionally structure ignorant. It just
 * writes byte by byte.
 */
void read_array(void) {
	byte start, len;
	byte *data = (byte*)&shared;

	uncons(&start);
	uncons(&len);

	// If trying to send too much, exit.
	if (start+len > sizeof(shared)) return;

	for (int i=start; i<start+len; i++) {
		uncons(data+i);
	}
}

/**
 * Refreshes the hardware and applies the changes.
 */
void hardware_write(void) {
	for (int i=0; i<belt_len; i++) {
		analogWrite(belt_leds[i],shared.belt[i]);
	}

	// Debug
	Serial.println("Refresh complete. Values: ");
	for (int i=0; i<belt_len; i++) {
		Serial.print(shared.belt[i],HEX);
		Serial.print(" ");
	}
	Serial.println("");

}


/**
 * Reads a command or character from Android. This function has two
 * operating modes. Returns command character, or not_command, if
 * payload byte is received instead.
 * 
 * Command mode: When pos is NULL, nothing is written to actual buffer
 * and receiving commands is allowed. If payload byte is received, it
 * is ignored.
 *
 * Payload mode: When pos is not NULL, a payload byte is expected and
 * written to the address of given pointer. If a command is received
 * instead, it returns without consuming anything.
 */
int uncons(byte *pos) {
	static byte unget_command = not_command;
	byte value;
	const bool payload_mode = pos != NULL;

	// Checks if there are ungetted commands
	if (unget_command != not_command) {
		byte tmp = unget_command;
		// Cleaning command status if in command mode.
		if (!payload_mode) unget_command = not_command;
		return tmp;
	}

	value = getc();

	// Escape character handling.
	if (value == escape) {
		value = getc();

		// Is it a command?
		if (value != not_command) {
			// In payload mode it is not consuming a command.
			if (payload_mode) unget_command = value;
			return value;
		}
		// Else, it's a literal escape.
		value = escape;
	}

	// Writing to pointer only when in payload mode. Otherwise the
	// output is ignored.
	if (payload_mode) *pos = value;
	
	return not_command;
}

/**
 * Gets a single character from Android. Should be used only by uncons()
 * to assure correct escape handling.
 */
byte getc() {
	static byte buffer[BUFFER_SIZE];
	static byte *ptr = NULL;
	static byte *end_ptr = NULL;

	if (ptr == end_ptr) {
		// Reading until we get something
		while (true) {
			// Do nothing if accessory is not ready.
			if (!acc.isConnected()) continue;

			int bytes = acc.read(&buffer, BUFFER_SIZE, timeout_naks);
			if (bytes < 1) {
				Serial.println("Ping.");
				continue;
			}
			if (bytes > BUFFER_SIZE) {
				Serial.println("Buffer overflow, dropping bytes.");
				bytes = BUFFER_SIZE;
			}
			ptr = (byte*)&buffer;
			end_ptr = (byte*)&buffer + bytes;
			break;
		}
	}
	return *(ptr++);
}
