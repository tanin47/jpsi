Java Electron
===============================================================================

It's like Electron but for Java. Now you can build cross-platform desktop apps with Java, JavaScript, HTML, and CSS.

The example app runs in MacOS's App Sandbox, can be notarized successfully, and is ~50MB in its final size. The example app currently works with Mac 
ARM. Other platforms are coming soon.

![Demo Application](demo.png)

The motivation of this example app is to support the desktop version of [Backdoor](https://github.com/tanin47/backdoor),
Database Querying and Editing Tool for you and your team.




How to run
-----------

1. Run `./gradlew run`.
2. Run `./gradlew jpackage` to build the DMG installer. Then, you can extract the DMG at `./build/jpackage`.
3. Run `./gradlew staple` to build, notarize, and staple the DMG.
