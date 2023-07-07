/*
  dec4IoT on Puck.JS
  Copyright (c) 2023 Jakob Kampichler

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  SOFTWARE.
*/

const storage = require('Storage');

const SERVICE = "34defd2c-c8fe-b18e-9a70-591970cba32b";

var CONFIG = {};

const filler = [0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF];

// Utility Functions BEGIN ---

function hours(hours) {
  return hours * 60 * 60 * 1000;
}

function minutes(minutes) {
  return minutes * 60 * 1000;
}

function seconds(seconds) {
  return seconds * 1000;
}

function splitUrlToPath(url) {
  let arr = url.split("/");
  arr.shift();
  arr.shift();
  arr.shift();
  return "/" + arr.join("/");
}

// Utility Functions END ---
// Config Functions BEGIN ---

function checkForValidConfig(fileName) {
  let cfgRegExp = new RegExp(fileName);
  let res = storage.list(cfgRegExp);

  if(res.length === 1) { return true; }
  return false;
}

function getConfig(fileName) {
  let isConfig = checkForValidConfig(fileName);
  if(!isConfig) {
    return false;
  } else if(isConfig) {
    return storage.readJSON(fileName);
  }
}

function writeConfig(fileName, id, endpoint, updateSensorsInterval) {    // Executed by Onboarding App
  if(!checkForValidConfig(fileName)) {
    let config = {
      "id": id,
      "api": endpoint,
      "update": updateSensorsInterval
    };

    storage.writeJSON(fileName, config);
  } else { return false; }
}

// Config Functions END ---
// Discovery Functions BEGIN ---  (Only executed by Onboarding App)

var LEDinterval = [];

function discovery() {
  let currentLed = 1;
  digitalWrite(eval(`LED${currentLed}`), 1);    /* jshint ignore:line */ // in this case it's (probably) not unsafe

  LEDinterval.push(setInterval(() => {
    digitalWrite(eval(`LED${currentLed}`), 0); /* jshint ignore:line */

    if(currentLed === 3) { currentLed = 1; } else { currentLed++; }

    digitalWrite(eval(`LED${currentLed}`), 1); /* jshint ignore:line */
  }, 750));
}

function discovered() {
  LEDinterval.forEach(e => {
    clearInterval(e);
  });
  LEDinterval = [];

  digitalWrite(LED1, 0);
  digitalWrite(LED2, 0);
  digitalWrite(LED3, 0);

  digitalWrite(LED1, 1);
  digitalWrite(LED2, 1);
  digitalWrite(LED3, 1);

  setTimeout(() => {
    digitalWrite(LED1, 0);
    digitalWrite(LED2, 0);
    digitalWrite(LED3, 0);
  }, 1000);
}

// Discovery Actions END ---

var bp_payl = 1;

var move_timer = 0;
var move_val_curr = 0;
var movement_timer_should = 5;
var current_movement_timer;

const EspDownlink = {   // Logic to communicate with ESP32
  characteristics: {},
  advertiseUUIDs: [],

  bindMe: function() {
    let keys = Object.keys(this);
    keys.forEach(e => {
      if(e === "bindMe") { return; }
      if(e.startsWith("__")) { return; }
      if(typeof this[e] !== "function") { return; }

      this[e] = this[e].bind(this);

      return;
    });
  },
  startAdvertising: function(name) {
    let advertiseThis = {};
    let services = {};
    services[SERVICE] = this.characteristics;
    advertiseThis[SERVICE] = undefined;

    NRF.setServices(services, { advertise: this.advertiseUUIDs, uart: false });

    NRF.setAdvertising({}, {
      "name": name,
      "showName": true,
      "manufacturer": 0x0590,
      "manufacturerData": JSON.stringify({})
    });
  },
  __charUpdate: function() {
    let services = {};
    services[SERVICE] = this.characteristics;

    try {
      NRF.updateServices(services);
    } catch(e) {
      if(e.message.includes("Can't update services until BLE restart")) { 
        for(let i=0;i<=100;i++) {
          console.log("Please disconnect from the Nordic UART inteface!");
        }
        return;
      }
      else { throw e; }
    }
  },
  addCharacteristic: function(uuid, data) {
    this.characteristics[uuid] = data;
    this.advertiseUUIDs.push(uuid);
    return this.characteristics;
  },
  updateCharacteristic: function(uuid, data) {
    this.characteristics[uuid] = data;
  },
  triggerUpdate: function() { this.__charUpdate(); }
};

(() => {    // Stupid JS thing
  EspDownlink.bindMe.bind(EspDownlink);
  EspDownlink.bindMe();
})();


const getSensorFuns = {
  "battery": Puck.getBatteryPercentage,
  "temperature": E.getTemperature,
};

const BleNumbers = {    // Assigned numbers from the BLE spec https://btprodspecificationrefs.blob.core.windows.net/assigned-numbers/Assigned%20Number%20Types/Assigned_Numbers.pdf
  "battery": 0x2A19,
  "button": 0x2AE2,
  "movement": 0x2C01,
  "temperature": 0x2A6E,
};

const sensors = [   // All Sensors that get sent
  'battery',
  'temperature',
  'movement',
  'button'
];

const BleInit = {   // Default BLE Characteristic initializer
  "value": filler,
  "readable": true,
  "writable": false,
  "notify": true
};

function updateCharacteristics(buttonPressed) {
  if(buttonPressed) {   // this effectively turns the button into a switch...
      let data = {
        "value": bp_payl,
        "notify": true
      };

      EspDownlink.updateCharacteristic(BleNumbers.button, data);

      digitalWrite(LED1, bp_payl);    // visual feedback

      bp_payl = !bp_payl;
  }

  sensors.forEach(e => {    // Update all sensors
    if(e === "movement" || e === "button") { return; }    // Except them, they have different logic

    let data = {
      "value": getSensorFuns[e](),
      "notify": true,
    };

    EspDownlink.updateCharacteristic(BleNumbers[e], data);
  });

  EspDownlink.triggerUpdate();    // Only trigger a hw update AFTER all the sensordata has been updated, if not the ESP panics
}

function watchButton() {    // if button was pressed, update the Characteristic
  setWatch(() => {
    updateCharacteristics(true);

    watchButton();
  }, BTN1);
}

// Movement Detection ---
function movementTimer() {    // Timer for movement detection
  let interval = setInterval(() => {
    if(move_timer >= 1) {
      move_timer--;
    }

    if(move_timer == 0 && move_val_curr == 1) {
      EspDownlink.updateCharacteristic(BleNumbers.movement, {
        "value": 0,
        "notify": true
      });

      EspDownlink.triggerUpdate();

      move_val_curr = 0;

      clearInterval(current_movement_timer);
      current_movement_timer = 0;
    }
  }, seconds(1));

  current_movement_timer = interval;
}

Puck.on('accel', a => {   // Accel event to trigger the timer
  if(move_val_curr == 0) {
    movementTimer();

    EspDownlink.updateCharacteristic(BleNumbers.movement, {
      "value": 1,
      "notify": true
    });

    EspDownlink.triggerUpdate();

    move_timer = movement_timer_should;
    move_val_curr = 1;
  }

  if(move_val_curr == 1) {
    move_timer = movement_timer_should;
  }
});
// ---

function executor() {
  let config = getConfig("main.json");    // Does the config exist?
  if(config === false) { return; }
  if(config) { CONFIG = config; }

  let sendableData = `i=${CONFIG.id};e=${splitUrlToPath(CONFIG.api)}`;

  EspDownlink.addCharacteristic(SERVICE, {    // Add config characteristic
    "value": sendableData,
    "readable": true,
    "writable": false,
    "description": "Device Configuration"
  });

  sensors.forEach(e => {    // Add a characteristic for each sensor
    EspDownlink.addCharacteristic(BleNumbers[e], BleInit);
  });

  setTimeout(() => {
    watchButton();

    EspDownlink.startAdvertising("dec4IoT Device");
    updateCharacteristics(false);
    require("puckjsv2-accel-bigmovement").on();   // We need this to make the movementtimer work

    setInterval(updateCharacteristics, hours(CONFIG.update), false);    // Update characteristics in set interval

  }, seconds(5));   // Start after 5 seconds to make sure that all characteristics are created properly
}
setTimeout(executor, seconds(15));    // only start after a set amount of time as to not lock one out of the Puck.JS! (the device can only handle one BLE connection / one BLE server at a time) 
