/*
    Filtered Device Mirror
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/

metadata {
    definition (
        name: "Generic Component Power Source Indicator",
        namespace: "evequefou",
        author: "Mike Bishop",
        importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-device-mirror/main/generic-component-powersource.groovy"
    ) {

    }
    preferences {
        capability "Sensor"
        capability "Power Source"
    }
}

void initialize() {}

void parse(List properties) {
    properties.each {
        sendEvent(it)
    }
}