# `sensors_iot/bangle-js` - dec4IoT Application for Bangle.JS

This folder contains the code that needs to be installed on a Bangle.JS device, to make it compatible with dec4IoT.

Content:
* [Developer Information](#developer-information)
  * [Guidelines](#guidelines)
  * [Deployment](#deployment)
* [Contributing](#contributing)
* [About](#about)
* [License](#license)


## Developer Information

### Guidelines
Do **not** change the variable/function names for [`startSetupIntent`](https://github.com/jkdev-io/BangleApps/blob/master/apps/dec4iot/app.js#L65C7-L65C23), [`sendDataIntent()`](https://github.com/jkdev-io/BangleApps/blob/master/apps/dec4iot/app.js#L66C7-L66C21) and [`startLogic()`](https://github.com/jkdev-io/BangleApps/blob/master/apps/dec4iot/app.js#L67C10-L67C20). They are used by the Android app.

### Deployment
#### Option 1:
* Use a version of the [App-Loader](https://github.com/jkdev-io/BangleApps), which already has the app uploaded.

#### Option 2 (if you want to continue development):
1. Setup a Bangle.Js Development environment

   First, fork the [`espruino/BangleApps`](https://github.com/espruino/BangleApps) repository. [Setup Github Pages](https://espruino.com/Bangle.js+App+Loader#enable-github-pages). Copy the `dec4iot` folder into the `apps` folder in the forked repository. Now open your favourite code editor and do as you please.

2. Upload the Code onto Bangle.Js

   Push your code and wait for the Pages Build to finish. After, open the Github Pages URL. Connect to your device and upload the app.
   **You will likely find a more updated version of this app at [jkdev-io/BangleApps](https://github.com/jkdev-io/BangleApps/tree/master/apps/dec4iot). Please copy the files from there!**


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
