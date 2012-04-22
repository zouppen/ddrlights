# Android–Arduino protocol

The protocol between Android and Arduino is using modified [Effect
Server] protocol. Effect Server is using unidirectional UDP protocol
for controlling DMX devices in a device-independent manner. See an
example of the [original protocol]. Unfortunately the best
documentation is written in Finnish.

The original protocol uses datagrams which have natural
boundaries. Because we are using asynchronous serial communication,
I've modified the protocol a bit to support character stuffing.

A packet starts with `BEGIN_PACKET` (0x7e01). It is followed by
arbitary number of Effect Server commands. A packet is terminated by
`END_PACKET` (0x7e02). If escape character (0x7e) occurs in data
transmit, it is replaced with `LITERAL_ESCAPE` (0x7e00).

Some parameters are left as endpoint-dependent: Maximum length of a
packet, supported effect types and (light) types. These should be
negotiated outside of this protocol.

Let's build a barebone example with two lights (values are in
hexadecimal):

    0: 0x01 # Version is always 1
    --
    1: 0x01 # Effect type: Light
    2: 0x00 # First light is at index 0
    3: 0x01 # In this case the light type is always "dimmer"
    4: 0xff # Maximum intensity
    --
    5: 0x01 # Effect type: Light
    6: 0x02 # Third light
    7: 0x01 # In this case the light type is always "dimmer"
    8: 0x7e # Almost half intensity
    
When encoded on wire (note the escaping of byte number 8):

    7e 01 01 00 01 ff 01 02 01 7e 00 7e 02
