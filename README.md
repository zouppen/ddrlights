# DDR lights

This is a project in progress to allow controlling of lights via
Android. This gives the opportunity to use all networking and sensor
capabilities already on Android and use Arduino for doing the actual
low-level control.

This project is based on the ideas presented in
[AllAboutEE article]. There is also YouTube video about an
[early version] of this project in action.

[AllAboutEE article]: http://allaboutee.com/2011/12/31/arduino-adk-board-blink-an-led-with-your-phone-code-and-explanation/ "Arduino ADK Board: Blink an LED With Your Phone, Minimum Code Required"

[Early version]: http://www.youtube.com/watch?v=s6tci4drXqQ "Android controls Arduino @ YouTube"

# Android–Arduino protocol

The protocol between Android and Arduino is using modified [Effect
Server] protocol. Effect Server is using unidirectional UDP protocol
for controlling DMX devices in a device-independent manner. 

The original protocol uses datagrams which have natural
boundaries. Because we are using asynchronous serial communication,
I've modified the protocol a bit to support character stuffing.

See [protocol specs].

[Effect Server]: http://effectserver.org/ "Effect Server"

[protocol specs]: protocol.md "Protocol specification (protocol.md)"

# Contact

My e-mail address is joel.lehtonen@iki.fi. Feel free to drop me an
e-mail if you have any feedback or ideas for future improvements.
