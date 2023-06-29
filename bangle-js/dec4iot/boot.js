/**
    dec4IoT on Bangle.js
    Copyright (C) 2023 Jakob Kampichler / jkdev

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <https://www.gnu.org/licenses/> or <https://choosealicense.com/licenses/gpl-3.0/>.
**/

var sensors = require('dec4iot_lib_sensor');

// Config Functions ===
function readConfig() {
    var reg = new RegExp("dec4iot.settings.json");
    var res = require('Storage').list(reg);

    var hasCfg = false;
    if(res.length === 1) hasCfg = true
    if(!hasCfg) return false;

    return require('Storage').readJSON("dec4iot.settings.json");
}

function writeDefaultConfig() {
    if(readConfig().configured) return false;

    require('Storage').writeJSON("dec4iot.settings.json", {
        "configured": false,
        
        "sensor_id": -1,
        "sensor_endpoint": "",
    
        "sensor_update_interval": -1
    });
}
// Config Functions ===

function logic(config) {
    let update_interval = config.sensor_update_interval;
    let sensor_id = config.sensor_id;
    let data_endpoint = config.sensor_endpoint;

    // setInterval(() => {
    //     try {
    //         sensors.gatherAllData().then(data => {
    //             http.post("https://eo32uzgizbfichl.m.pipedream.net", data, {});
    //         });

    //     } catch(e) {
    //         console.log(e);
    //     }

    // }, update_interval * 1000 * 60);

}

var confCheck = setInterval(() => {
    var config = readConfig();
    if(config === false) writeDefaultConfig()
    else if(config.configured) {
        clearInterval(confCheck);
        logic(config);
    }
}, 60 * 1000 * 60)