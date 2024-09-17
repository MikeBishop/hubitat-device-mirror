/*
    Filtered Device Mirror
    Copyright 2024 Mike Bishop,  All Rights Reserved
*/

metadata {
    definition (
        name: "Generic Component Battery",
        namespace: "evequefou",
        author: "Mike Bishop",
        importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-device-mirror/main/generic-component-battery.groovy"
    ) {

    }
    preferences {
        capability "Sensor"
        capability "Battery"
    }
}

void initialize() {}

void parse(List properties) {
    properties.each {
        sendEvent(it)
    }
}
