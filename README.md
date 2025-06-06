# Starlink Dashboard
This is a simple application that displays dish diagnostics provided from the dish's local gRPC server.
If you rely primarily on Starlink for internet, then you may not be able to log into Starlink's website to check the status of the dish if something seems wrong.
This application aims to provide you with diagnostics info as easily as possible, even if the dish is offline.
NOTE: The model of your Starlink terminal, software version, or service plan may limit functionality of the local gRPC server on your dish, thereby limiting the usefulness of this application.
Always test things before you need them!

## Usage
Directly connect your computer to the modem's ethernet connection (or properly network your connection using other methods in accordance with the information below).
The dish's diagnostic server is always at the address 192.168.100.1 using port 9200, so your computer must be on that same network.
I'm not sure how large the 192.168.100.0 network is, but most conventions would indicate that is a /24 network.
I personally use 192.168.100.2 for my computer's IP address without issue.
Next, start the application.
It will automatically request diagnostic information from the terminal every 5 seconds and update the application's main display.
If you select File -> Show response text, a new window will appear that provides a formatted textual representation of the diagnostic information that was last received.  This includes everything displayed on the main display, as well as additional, and generally less relevant information in a text format.

## Building
A compiled release, both as a JAR and a windows executable, is available if you do not wish to build from source.
Dependencies are managed using Maven.
The main package, fibrous.starlink, contains the application specific classes, with StarlinkDashboard being the main one.
There is another package, fibrous.starlink.testing, that contains a test server that runs on port 9200 to test against and provides a simulated diagnostic response.
When you perform mvn build, maven should automatically create the classes needed for the protobuf definition.
I prefer Java 21, but it is not needed for this application.
I believe this will run on Java 8 if you really want to.

## Other Resources
This application was built using info provided from SpaceX here: https://github.com/SpaceExplorationTechnologies/enterprise-api/tree/master

## Legal
THIS APPLICATION AND ITS SOURCE IS DISTRIBUTED UNDER THE 3-CLAUSE BSD LICENSE AND COMES WITH NO WARRANTY OF ANY KIND.
See the file "LICENSE" for more details.
This is a 3rd-party application with no direct affiliation with SpaceX, its subsidaries, or any related parties.
The file "device.proto" is provided by SpaceX, and can be found at the link provided in the Other Resources section.
