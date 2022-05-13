/*
    Filtered Device Mirror
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field

@Field static final List DeviceTypes = [
    [type: "Presence", input: "presenceSensors", capability: "capability.presenceSensor", properties: ["presence"], driver: "Generic Component Presence Sensor"]
]

definition (
    name: "Filtered Device Mirror", namespace: "evequefou", author: "Mike Bishop", description: "Mirror one capability of a complex device",
    // importUrl: "TBD",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

Map mainPage() {
    dynamicPage(name: "Main Page", title: "Filtered Device Mirror", install: true, uninstall: true) {
        section("General") {
            input "thisName", "text", title: "Name this Mirror Set", submitOnChange: true
			if(thisName) app.updateLabel("$thisName")
        }
        section("Devices") {
            DeviceTypes.each {
                input it.input, it.capability, title: "${it.type} devices to mirror", required: false, multiple: true
            }
        }
        section("Settings") {
            input "debugSpew", "bool", title: "Log debug events", defaultValue: false
        }
    }
}

private getParentDevice() {
    def dni = "Filtered-" + app.id.toString()
    def parentDevice = getChildDevice(dni)
    if (!parentDevice) {
        debug "creating Filtered Device: ${dni}"
        parentDevice = addChildDevice("evequefou", "Filtered Devices", dni, null,
             [name: "Filtered Devices", label: thisName ?: "Filtered Devices", completedSetup: true ])
    } else {
        parentDevice.initialize()
    }
    return parentDevice
}

void updated() {
	unsubscribe()
	initialize()
}

void installed() {
	initialize()
}

void initialize() {
    DeviceTypes.each {
        def deviceType = it
        def devices = settings[deviceType.input].findAll { !myDevice(it.getDeviceNetworkId) }
        deviceType.properties.each {
            subscribe(devices, it, handler)
        }
        def deviceIds = devices*.getDeviceNetworkId()
        deviceIds.each {
            refresh(it)
        }
        getParentDevice().removeChildrenExcept(deviceType.type, deviceIds)
    }
}

void refresh() {
    initialize()
}

void refresh(id, properties = null) {
    def root = getParentDevice()
    debug "Refreshing ${id}"
    if( myDevice(id) ) {
        return
    }
    if (root) {
        def deviceTypes = DeviceTypes

        // If we were told what properties to refresh, only care about types with
        // those properties.
        if (properties) {
            deviceTypes = DeviceTypes.findAll{ it.properties.any {properties.contains(it) } }
        }

        deviceTypes.each {
            def deviceType = it
            def source = settings[deviceType.input].find { it.getDeviceNetworkId() == id }
            if (source) {
                def props = deviceType.properties
                if( properties ) {
                    props = props.findAll{ properties.contains(it) }
                }
                root.parse(
                    properties.collect {
                        [
                            id: id,
                            name: source.getLabel() ?: source.getName(),
                            type: deviceType,
                            properties: properties.collect {[
                                name: it,
                                value: source.currentValue(it)
                            ]}
                        ]
                    }
                )
            }
        }
    }
}

void handler(evt) {
    debug "Received ${evt}"
    refresh(evt.device.getDeviceNetworkId(), [evt.name])
}

void debug(String msg) {
    if( debugSpew ) {
        log.debug msg
    }
}

Boolean myDevice(id) {
    return id && id.startsWith("Filtered-${app.id}-")
}

def getDeviceTypes() {
    return DeviceTypes
}
