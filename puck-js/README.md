# `sensors_iot/puck-js` - dec4IoT Application for Puck.JS

This folder contains the code that needs to be installed on a Puck.JS device, to make it compatible with dec4IoT.

Content:
* [Developer Information](#developer-information)
  * [Guidelines](#guidelines)
  * [Deployment](#deployment)
* [Contributing](#contributing)
* [About](#about)
* [License](#license)

## Developer Information

### Guidelines
Do **not** change the function names for [`discovery()`](https://github.com/dec112/sensors_iot/blob/main/puck-js/puck_js-latest.js#L83C10-L83C19), [`discovered()`](https://github.com/dec112/sensors_iot/blob/main/puck-js/puck_js-latest.js#L96C10-L96C20) and [`writeConfig()`](https://github.com/dec112/sensors_iot/blob/main/puck-js/puck_js-latest.js#L66C10-L66C21). They are used by the Onboarding app for setup of the Puck.JS's config.

### Deployment

#### Flash the dec4IoT firmware onto Puck.JS
There is a guide available [here](puck-setup.md)

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
