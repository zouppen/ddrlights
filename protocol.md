# Android–Arduino protocol

# Proposal A

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

[Effect Server]: http://effectserver.org/ "Effect Server"

[original protocol]: http://blog.instanssi.org/2012/01/effect-server-ohjelmoitavat-valot-20.html "Effect Server - Ohjelmoitavat Valot 2.0"

# Proposal B

The protocol between Android and Arduino is simple "register based"
format. The sender must know the format of the byte array. This makes
the latencies short and the microcontroller code simpler. The downside
of this approach is that the Android client must know the internal
details of microcontroller memory. Usually this is not a problem.

Command starts with `BEGIN_WRITE` (0x7e01). It is followed by seek and
length parameters. If you write the whole array, seek parameter is
zero and length parameter is the length of whole array.

Encoding of header bytes (seek and length) depends on the
architecture. The recommendation is to use unsigned big-endian format
and use regular integer lengths, which are Word8, Word16 and
Word32. Same encoding is used for seek and length parameters.

If 0x7e ocurrs in the payload, it is replaced by `LITERAL_ESCAPE`
(0x7e00). The receiver side does the opposite and replaces
`LITERAL_ESCAPE` with 0x7e.

The sender should issue `REFRESH` (0x7e02) command after updating the
registers. The receiver should *atomically* (or as atomically as
desired) update its "characteristics", like the PWM duty cycles of
individual pixels. Separate refresh command is used because there may
be multiple array updates in different byte positions and we want to
be sure the array is updated as one pass to avoid tearing in output.

Finally, the receiver is set to `IDLE` mode where it ignores every
byte sequence but commands like `BEGIN_WRITE`.

## DDR lights

The array size is 6 bytes and header bytes are encoded as unsigned 8 bit
integers. Bytes 0–5 represent lights 1–6 and byte values correspond to
intensity of each light. Light is turned off when value is 0x00 and
has full intensity when value is 0xff.

Example 1: Turning lamp #3 to almost half intensity of 0x7e (note:
it's escape character):

    7e 01 02 01 7e 00 7e 02

Example 2: Setting all lamps to maximum intensity:

    7e 01 00 06 ff ff ff ff ff ff 7e 02
    
Example 3: Sets lamp #3 *on* and #6 *off*. This is a bit unpractical
but shows the idea of multiple writes per "frame":

    7e 01 02 01 ff 7e 01 05 01 00 7e 02

In the first example seek was 2 and bytes written was 1. In second
example, we started from the beginning (seek 0) and wrote the whole
array (length 6). In third example we had 2 writes (to positions 3 and
6) but only one `REFRESH` command in the end.

## Elovalo cube

This is not going to be supported by this project but the aim is to be
compatible with this. Led cube contains 8^3 LEDs and only on/off
states are supported. Therefore minimum array length is 64 bytes and a
voxel represents a bit in the array. Header bytes are encoded as
unsigned 8 bit integers. Every LED row consumes 8^2 bits (8 bytes). A
single bar of 8 LEDs is encoded as a single byte.

If the cube is built using PWM technology, a single LED may have 256
levels of brightness. In that case 8^3 cube array length would be 512
bytes and a single byte represents brightness of a single
voxel. Header bytes should be encoded using unsigned big-endian 16-bit
integers.

Numbering system of LEDs (the mapping from bytes to LEDs) is not yet
specified.
