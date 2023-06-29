## esp32

### 
- to support MAC in Notfiy callback, following changes are required

BLERemoteCharacteristic.h
`````
~/.platformio/packages/framework-arduinoespressif32/libraries/BLE/src/BLERemoteCharacteristic.h
`````

line 25, add <em>esp_bd_addr_t addr,</em>

`````
typedef std::function<void(BLERemoteCharacteristic* pBLERemoteCharacteristic, esp_bd_addr_t addr, uint8_t* pData, size_t length, bool isNotify)> notify_callback;
`````

BLERemoteCharacteristic.cpp

`````
~/..platformio/packages/framework-arduinoespressif32/libraries/BLE/src/BLERemoteCharacteristic.cpp
`````

line 166, add <em>evtParam->notify.remote_bda,</em>

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

### platformio
- platformio.ini

`````
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino
monitor_speed = 115200
upload_port = /dev/ttyUSB*
board_build.partitions = huge_app.csv
`````
