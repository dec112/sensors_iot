# `sensors_iot/onboarding-android` - dec4IoT Application for Android

This folder contains the application that is needed to configure Bangle.JS and Puck.JS devices and to send Emergency Data from Bangle.JS 

Content:
* [User Information](#user-information)
    * [Device Setup Guide](#device-setup-guide)
* [Developer Information](#developer-information)
    * [Deployment](#deployment)
* [Contributing](#contributing)
* [About](#about)
* [License](#license)

## User Information

This app sets up Bangle.JS and Puck.JS for the dec4IoT project, and serves as the bridge between Bangle.JS and the dec4IoT [Semantic Container](https://github.com/dec112/dc-iot).

### Device Setup Guide

**BEFORE YOU BEGIN:**
* You need a dec4IoT Device Setup QR-Code.
* In the 3-dot-menu, at least press "Check for Modules" and "Check for Permission". Please grant all permission requests that come up with this dialogue, as they are required for device setup
* If you plan to use Bangle.JS: Press "Check for Location Permissions" and "Check for Battery Optimisations". Grant all of these permissions as well, otherwise Emergency Calls from your Bangle.JS might not be made properly.
* Please also note, that Bangle.JS requires the "Bangle.JS Gadgetbridge" app to be set up on your phone. There are instructions for this in the "dec4IoT" app. As the App Loader URL in "Bangle.JS Gadgetbridge", use "https://jkdev-io.github.io/BangleApps/android.html".

**The actual guide:**
1. Once you have everything set up, press "Start"
2. Scan the QR-Code
3. Choose your device.
#### for Puck.JS
4. Before you continue, you need to install the dec4IoT code onto your Puck.JS. Follow the instructions over at [`sensors_iot/puck-js`](https://github.com/dec112/sensors_iot/blob/main/puck-js/puck-setup.md)
5. After doing that, the app will show your Puck.JS in a list. Press on the button that has the name of your Puck.JS. It will start rotating colours, if you set up the dec4IoT code correctly and you chose the right device.
6. Press "Continue". Your Puck.JS will stop flashing colours, and you will be presented with a new dialogue, where you need to confirm the Sensor ID and API Endpoint. Tick the checkbox if everything is right, else press the green "Restart" button.
7. Press "Continue". You are now done, your Puck.JS should be configured for use with dec4IoT.
#### for Bangle.JS
4. Here you will find the Instructions to set up your Bangle.JS, which were referenced in the "Before you begin" section.
5. Open up the "dec4IoT Companion" on your Bangle.JS. The Android App might automatically continue the setup process once you have done that. If not, close down the "dec4IoT" app completely, and after that, restart the "dec4IoT Companion" on your Bangle.JS
6. You will now be presented with a new dialogue, where you need to confirm the Sensor ID and API Endpoint. Tick the checkbox if everything is right, else press the green "Restart" button.
7. Press "Continue". You are now done, your Bangle.JS should be configured for use with dec4IoT.
   
## Developer Information

### Deployment

Either download a prebuild APK from GitHub Actions, clone this Repo or download the Repository's Zip File.

#### Download prebuilt APK
In the "[Generate APK](https://github.com/jkampich1411/dec4iot-onboarding-android/actions/workflows/build-android-app.yml)" Action, choose the latest Actions Run and download the Artifact ZIP.
In there you will find an "app-debug.apk", which you can deploy onto your devices.

#### Building from Android Studio
After cloning this Repo / downloading the ZIP, open it in [Android Studio](https://developer.android.com/studio), export an APK via "Build" > "Build Bundle(s) / APK(s)" > "Build APK(s)".

#### Building from the command line
* Make sure you have Java 17 or newer installed.
* After cloning this Repo / downloading the ZIP, open a Terminal Window in the folder you cloned to / extracted the Zip into.
* Depending on your OS, run `./gradlew build` (Here Windows)
* Then, run `./gradlew assemble` (Windows, again)

## Contributing

Please report bugs and suggestions for new features using the [GitHub Issue-Tracker](https://github.com/dec112/dc-iot/issues) and follow the [Contributor Guidelines](https://github.com/twbs/ratchet/blob/master/CONTRIBUTING.md).

If you want to contribute, please follow these steps:

1. Fork it!
2. Create a feature branch: `git checkout -b my-new-feature`
3. Commit changes: `git commit -am 'Add some feature'`
4. Push into branch: `git push origin my-new-feature`
5. Send a Pull Request



## About

<img align="right" src="https://raw.githubusercontent.com/dec112/dc-iot/main/app/assets/images/netidee.jpeg" height="150">This project has received funding from [Netidee Call 17](https://netidee.at).

<br clear="both" />

## License

[MIT License 2023 - DEC112](https://raw.githubusercontent.com/dec112/dc-iot/main/LICENSE)
