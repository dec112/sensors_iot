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

let acclCb = (a) => {};
let compCb = (a) => {};
let baroCb = (a) => {};
let hrmCb = (a) => {};

function activateAcceleration() {
    Bangle.on('accel', acclCb);
}
function deactivateAcceleration() {
    Bangle.on('accel', (x) => {})
}

function activateCompass() {
    Bangle.setCompassPower(1);
    Bangle.on('mag', compCb);
}
function deactivateCompass() {
    Bangle.setCompassPower(0);
    Bangle.on('mag', (x) => {});
}

function activateBarometer() {
    Bangle.setBarometerPower(true);
    Bangle.on('pressure', baroCb);
}
function deactivateBarometer() {
    Bangle.setBarometerPower(false);
    Bangle.on('pressure', (x) => {});
}

function activateHRM() {
    Bangle.setHRMPower(true, "dec4iot");
    Bangle.on('HRM', hrmCb);
}
function deactivateHRM() {
    Bangle.setHRMPower(false, "dec4iot");
    Bangle.on('HRM', (x) => {})
}

function activateAll() {
    activateAcceleration();
    activateBarometer();
    activateCompass();
    activateHRM();
}
function deactivateAll() {
    deactivateAcceleration();
    deactivateBarometer();
    deactivateCompass();
    deactivateHRM();
}

function allTrue(object) {
    for (let key in object) {
        if (!object[key]) {
            return false;
        }
    }
    return true;
}

function gatherAllData() {
    return new Promise((res, rej) => {
        activateAll();

        currentlyGathering = true;

        let done = {
            acc: false,
            com: false,
            bar: false,
            hrm: false,
            bat: false
        };
        
        let data = {
            acc: {},
            com: {},
            bar: {},
            hrm: {},
            bat: 0
        };

        data.acc = Bangle.getAccel();
        done.acc = true;

        data.com = Bangle.getCompass();
        done.com = true;

        data.bat = E.getBattery();
        done.bat = true;

        data.hrm = Bangle.getHealthStatus();
        done.hrm = true;

        Bangle.getPressure().then(pressure => {
            data.bar = pressure;
            done.bar = true;
        });

        let intervalId = setInterval(() => {
            if(allTrue(done)) {
                deactivateAll();
                clearInterval(intervalId);
                res(data);
            }
        }, 500);
    });
}

module.exports = {
    acclCb,
    compCb,
    baroCb,
    hrmCb,

    activateAcceleration,
    activateBarometer,
    activateCompass,
    activateHRM,

    deactivateAcceleration,
    deactivateBarometer,
    deactivateCompass,
    deactivateHRM,

    gatherAllData
}