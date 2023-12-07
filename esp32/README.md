
This repository provides a comprehensive solution for monitoring IoT devices, with this part describing the ESP32 BLE/WiFi Gateway. With this software, users can integrate sensors (Puck.js) and monitor the status, performance, and any anomalies. The system continuously collects data from the connected devices and analyzes it to determine if they are functioning correctly or if potential problems might arise. Upon detecting unusual activities or when device parameters fall outside the established normal range, alarms are immediately triggered. These alerts can be relayed through various channels such as email, or alerting through the DEC112 ESInet. The repository includes both the code for data collection and analysis, as well as a user-friendly interface for configuring monitoring settings and alarm conditions. The aim of this project is to provide a robust and scalable solution to ensure the efficiency and security of IoT networks. The current repository includes the ESP32 BLE/WiFi Gateway as shown in the figure below.

<img align="center" src="https://raw.githubusercontent.com/dec112/dc-iot/main/app/assets/images/system.png" height="400">

Content:
* [Further Resources](#further-resources)
* [Developer Information](#developer-information)
    * [Deployment](#deployment)
    * [Sample Data and Typical Workflow](#sample-data-and-typical-workflow)
* [Issues](#issues)
* [About](#about)
* [License](#license)

## Further Resources
* Project description: https://www.netidee.at/dec4iot    
* Semantic Container: https://github.com/OwnYourData/semcon    
* Blogpost on Monitoring (in German): https://www.netidee.at/dec4iot/system-monitoring-fuer-dec4iot

## Developer and User Information

### ESP32-HW

We recommend the following hardware for this project: [ESP32 NodeMCU](https://www.berrybase.at/esp32-nodemcu-development-board?c=2473)

### IDE

The ESP32 BLE/WiFi Gateway was created with [PlatformIO IDE](https://platformio.org/) and VS Code (alternatively, [Arduino IDE](https://www.arduino.cc/en/software) can also be used). Besides the platform 'espressif32', the board 'nodemcu-32s' and the 'arduino' framework, no further libraries are necessary. The following additions are required:

- to support MAC in Notfiy callback, following changes are required in

_BLERemoteCharacteristic.h_
`````
~/.platformio/packages/framework-arduinoespressif32/libraries/BLE/src/BLERemoteCharacteristic.h
`````

line 25: add <em>esp_bd_addr_t addr,</em>

`````
typedef std::function<void(BLERemoteCharacteristic* pBLERemoteCharacteristic, esp_bd_addr_t addr, uint8_t* pData, size_t length, bool isNotify)> notify_callback;
`````

_BLERemoteCharacteristic.cpp_

`````
~/..platformio/packages/framework-arduinoespressif32/libraries/BLE/src/BLERemoteCharacteristic.cpp
`````

line 166: add <em>evtParam->notify.remote_bda,</em>

`````
m_notifyCallback(this, evtParam->notify.remote_bda, evtParam->notify.value, evtParam->notify.value_len, evtParam->notify.is_notify);
`````

- Callback example

just add <em>BLEAddress BLEAddr,</em> as shown below

`````
static void notifyButtonCallback(BLERemoteCharacteristic* pBLERemoteCharacteristic, BLEAddress BLEAddr, uint8_t* pData, size_t length, bool isNotify)
...
  Serial.printf(" ADDR[%s] ", BLEAddr.toString().c_str());
...
`````
- platformio.ini

`````
[env:esp32dev]
platform = espressif32
board = nodemcu-32s
framework = arduino
monitor_speed = 115200
upload_port = /dev/ttyUSB*
board_build.partitions = huge_app.csv
`````

### ESP32-SW

Two files in the project are sufficient to enable all functions of the ESP32 BLE/Wifi GW:
- **json.h**: is an integrated library to provide essential JSON functionality for the project. The library was supplemented by the _jsonb_float()_ function.
- **main.cpp**: includes ESP32 setup and loop functions as well as callback and help functions for operating the GW

Since a connection can be initiated with any BLE device, we filter our devices (Puck.js) based on their MAC addresses (According to the standard, a maximum of 4 devices can be connected). It is therefore necessary to store these in the source code as shown below. In addition, a name of the GW **DEV_NAME** can be selected and an adjustment of the time zone **GMT_OFF_SEC** as well as summer or winter time **DLT_OFF_SEC** can be set
```
// adjust this part if necessary
#define DEV_NAME "DEC112-BLE-Client"
#define MAC_1 "68:72:c3:eb:8e:a9"
#define MAC_2 "d6:ea:13:f5:11:3b"
#define MAC_3 "fa:45:e3:78:45:ad"
#define MAC_4 "cc:e0:e7:20:43:85"
#define URL_PREFIX "https://"
#define GMT_OFF_SEC 3600
#define DLT_OFF_SEC 0
//
```


Finally, the data for the WiFi used must be entered and then the firmware can be built and uploaded. For permanent monitoring, the monitoring option from PlatformIO (VS Code) is recommended. 
```
// adjust this part if necessary
const char *ssid = "xxxxxx";
const char *password = "xxxxxx";
const char *ntpServer = "pool.ntp.org";
```


