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
var Layout = require('Layout');

// clear the screen
g.clear();
g.reset().clearRect(Bangle.appRect);

var big_img = {
  width : 80, height : 80, bpp : 3,
  transparent : 0,
  buffer : require("heatshrink").decompress(atob("AAcJkmSpICQoAcGDSQCEDgkEDq+ADgUBDSoCDDsAaXAQVIDr0CDrVJgESDvMgDvYaZARn/AAXJDrn5DvQAFEaYdJ//8Drn/+QdcXiIdMPSAdM/4dd8gd6ah+fDxryRAQknDrhEGDq+Sv4dD+Qd1p4d//wdXpIdOn5KNDqQOKDv4d/Dv4d/DvUnBwXyDrACNp4dhJRQdrv4d5yYcDDq7dDAAQXOz4VFAA4dODhv8DvXkDritPDhn5DrnJDrfyDRodNWB4dMOiAdGC6YdnVSACdiQdbkAd/ATEAgQdcgAdapAdfgIdZDgIdegEEDq+ADocAdi4cEAAMJDqdADIYA=="))
};
//#region  Config Functions
function readConfig() {
    var reg = new RegExp("dec4iot.settings.json");
    var res = require('Storage').list(reg);

    var hasCfg = false;
    if(res.length === 1) hasCfg = true
    if(!hasCfg) return false;

    return require('Storage').readJSON("dec4iot.settings.json");
}

var config = readConfig();

function writeDefaultConfig() {
    if(readConfig().configured) return false;

    require('Storage').writeJSON("dec4iot.settings.json", {
        "configured": false,
        
        "sensor_id": -1,
        "sensor_endpoint": "",
    
        "sensor_update_interval": -1
    });
}
//#endregion  Config Functions

//#region  Android
const startSetupIntent = JSON.stringify({t: "intent", target: "activity", action: "me.byjkdev.dec4iot.intents.banglejs.SETUP", flags: ["FLAG_ACTIVITY_NEW_TASK"]});  // Sending this to Gadgetbridge will start Onboarding App
const sendDataIntent = (data) => { return JSON.stringify({t: "intent", target: "broadcastreceiver", action: "me.byjkdev.dec4iot.intents.banglejs.SEND_DATA", "extra": { "json_data": data }, package: "run.jkdev.dec4iot.jetpack", class: "run.jkdev.dec4iot.jetpack.BangleJsDataReceiver", mimetype: "application/json"}); } // Sending this to Gadgetbridge will tell Onboarding App to send data
function startLogic() {  // run by onboarding app; start logic so no app restart is needed
    g.clear();
    g.reset().clearRect(Bangle.appRect)
    logic(readConfig())
}
//#endregion Android

//#region  Logic
function EMERGENCY() {
    sensors.gatherAllData().then(data => {
        let sendMe = {
            "info": {
                "sensor_id": config.sensor_id,
                "sensor_endpoint": config.sensor_endpoint,
                "mac_address": NRF.getAddress(),

                "bpm_only": false,
                "trigger_manual": true
            },
            "data": data
        };
        
        let dataIntent = sendDataIntent(JSON.stringify(sendMe));
        Bluetooth.println("\n" + dataIntent);

        Bangle.on('hrm', hrm => {
            Terminal.println("HRM EVENT RECEIVED")

            let sendMe = {
                "info": {
                    "sensor_id": config.sensor_id,
                    "sensor_endpoint": config.sensor_endpoint,
                    "mac_address": NRF.getAddress(),

                    "bpm_only": true,
                    "trigger_manual": true
                },
                "data": { "hrm": hrm }
            };

            let dataIntent = sendDataIntent(JSON.stringify(sendMe));
            Bluetooth.println("\n" + dataIntent);
            sensors.deactivateHRM();

            Bangle.buzz(1000, 1).then(() => Bangle.showClock());
        });
        sensors.activateHRM()
        Terminal.println("HRM ACTIVATED")
    });
}

function logic(config) {
    sensors.activateAcceleration();
    sensors.activateBarometer();
    sensors.activateCompass();

    var layout_start = new Layout({
        type: "v", c: [
            {type: "img", pad: 8, src: big_img},
            {type: "txt", font: "Vector:14", label: "Press button to call", id: "press_button_to_call"},
            {type: "txt", font: "Vector:14", label: "Emergency Services", id: "emergency_services"},
            {type: "btn", pad: 4, label: "SOS", cb: () => {
                E.showPrompt("Please confirm:", {"title": "Emergency", "buttons": {"SOS": 1, "Cancel": 2}}).then(i => {
                    if(i === 1) EMERGENCY();
                    if(i === 2) Bangle.showClock()
                });
            }}
    ]}, {
        btns: [
            {label: "SOS", cb: () => { EMERGENCY() }}
        ]
    });

    layout_start.render();
    
    // Load widgets
    Bangle.loadWidgets();
    Bangle.drawWidgets();
}

//#endregion  Logic

//#region  Setup
function showSetupMsgs() {
    let titleHeight = 60;

    // display messages
    g.setFont("Vector:64").setFontAlign(0, 0).drawString("Setup", g.getWidth() /2 + 4, titleHeight);
    g.setFont("Vector:18").setFontAlign(0, 0).drawString("Check your phone!", g.getWidth() /2, titleHeight + 48);
    g.setFont("6x8").setFontAlign(0, 0).drawString("Continue using the dialogue", g.getWidth() /2, titleHeight + 73);
    g.setFont("6x8").setFontAlign(0, 0).drawString("in the dec4IoT Onboarding App", g.getWidth() /2, titleHeight + 83);

    // Load widgets
    Bangle.loadWidgets();
    Bangle.drawWidgets();
}

if(config === false || !config.configured) {  // No config file found?
    writeDefaultConfig();
    Bluetooth.println("\n" + startSetupIntent);  // Start setup!
    showSetupMsgs();
} else logic(config)
//#endregion  Setup