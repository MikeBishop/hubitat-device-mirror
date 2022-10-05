/*
    Filtered Device Mirror
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field

@Field static final List DeviceTypes = [
    [type: "Presence", input: "presenceSensors", capability: "capability.presenceSensor", properties: ["presence"], namespace: "hubitat", driver: "Generic Component Presence Sensor"],
    [type: "Power Meter", input: "powerMeters", capability: "capability.powerMeter", properties: ["power"], namespace: "hubitat", driver: "Generic Component Power Meter"],
    [type: "Power Source", input: "powerSources", capability: "capability.powerSource", properties: ["powerSource"], namespace: "evequefou", driver: "Generic Component Power Source Indicator"],
    [type: "Switch", input: "switches", capability: "capability.switch", properties: ["switch"], namespace: "hubitat", driver: "Generic Component Switch"]
]

definition (
    name: "Filtered Device Mirror", namespace: "evequefou", author: "Mike Bishop", description: "Mirror one capability of a complex device",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-device-mirror/main/filtered-device.groovy",
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
            input "thisName", "text", title: "Name this Mirror Set"
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

private getRootDevice() {
    def dni = "Filtered-" + app.id.toString()
    def rootDevice = getChildDevice(dni)
    def rootLabel = thisName ?: "Filtered Devices"
    if (!rootDevice) {
        debug "creating Filtered Device: ${dni}"
        rootDevice = addChildDevice("evequefou", "Filtered Devices", dni, null,
             [name: "Filtered Devices", label: rootLabel, completedSetup: true ])
    }
    if (rootDevice.getLabel() != rootLabel) {
        rootDevice.setLabel(rootLabel)
    }
    return rootDevice
}

void updated() {
	unsubscribe()
	initialize()
}

void installed() {
	initialize()
}

void initialize() {
    def root = getRootDevice()
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
        root.removeChildrenExcept(deviceType.type, deviceIds)
    }
    root.removeChildrenExcept("null", [])
}

void refresh() {
    initialize()
}

void refresh(id, properties = null) {
    def root = getRootDevice()
    debug "Refreshing ${id}"
    if( myDevice(id) ) {
        return
    }
    if (root) {
        def deviceTypes = DeviceTypes

        debug "Trying to refresh ${id}"
        deviceTypes.each {
            def deviceType = it
            // If we were told what properties to refresh, only look at those
            // properties.
            def props = deviceType.properties
            if( properties ) {
                props = props.findAll{ properties.contains(it) }
            }

            // If that includes any properties for this type, refresh them.
            if (props.size > 0) {
                def source = settings[deviceType.input].find { it.getDeviceNetworkId() == id }
                if (source) {
                    root.parse(
                        [[
                            id: id,
                            name: source.getLabel() ?: source.getName(),
                            type: deviceType,
                            properties: props.collect {[
                                name: it,
                                value: source.currentValue(it)
                            ]}
                        ]]
                    )
                }
            }
        }
    }
}

void mirrorOn(id) {
    def target = switches.find { it.getDeviceNetworkId() == id }
    if (target) {
        target.on()
    }
}

void mirrorOff(id) {
    def target = switches.find { it.getDeviceNetworkId() == id }
    if (target) {
        target.off()
    }
}

void handler(evt) {
    debug "Received ${evt.name} event (${evt.value}) from ${evt.device}"
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
