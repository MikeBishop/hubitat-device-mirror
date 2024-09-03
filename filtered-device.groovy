/*
    Filtered Device Mirror
    Copyright 2022-2024 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field

@Field static final List DeviceTypes = [
    [type: "Contact", input: "contacts", capability: "capability.contactSensor", properties: ["contact"], namespace: "hubitat", driver: "Generic Component Contact Sensor"],
    [type: "Motion", input: "motionSensors", capability: "capability.motionSensor", properties: ["motion"], namespace: "hubitat", driver: "Generic Component Motion Sensor"],
    [type: "Presence", input: "presenceSensors", capability: "capability.presenceSensor", properties: ["presence"], namespace: "hubitat", driver: "Generic Component Presence Sensor"],
    [type: "Power Meter", input: "powerMeters", capability: "capability.powerMeter", properties: ["power"], namespace: "hubitat", driver: "Generic Component Power Meter"],
    [type: "Power Source", input: "powerSources", capability: "capability.powerSource", properties: ["powerSource"], namespace: "evequefou", driver: "Generic Component Power Source Indicator"],
    [type: "Switch", input: "switches", capability: "capability.switch", properties: ["switch"], namespace: "hubitat", driver: "Generic Component Switch", funcs: ["on", "off"]],
    [type: "Battery", input: "batteries", capability: "capability.battery", properties: ["battery"], namespace: "evequefou", driver: "Generic Component Battery"],
    [type: "Lock", input: "locks", capability: "capability.lock", properties: ["lock"], namespace: "hubitat", driver: "Generic Component Lock", funcs: ["lock", "unlock"]],
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

        devices.each { refreshByDevice(it, deviceType) }
        root.removeChildrenExcept(deviceType.type, devices*.getDeviceNetworkId())
    }
    root.removeChildrenExcept("null", []);
    root.removeChildrenExcept("Contact Sensor", []);
}

void refresh() {
    initialize()
}

void refreshById(id, type) {
    def source = settings[type.input].find { it.getDeviceNetworkId() == id }
    if( source ) {
        refreshByDevice(source, type)
    }
    else {
        warn "Could not find device ${id} of type ${type.type}"
    }
}

void refreshByDevice(source, type) {
    getRootDevice().parse(
        [[
            id: source.getDeviceNetworkId(),
            name: source.getLabel() ?: source.getName(),
            type: type,
            properties: type.properties.collect {[
                name: it,
                value: source.currentValue(it)
            ]}
        ]]
    )
}

void mirrorFunc(def typeName, def id, def func, def args) {
    def type = DeviceTypes.find { it.type == typeName }
    def child = settings[type.input].find { it.getDeviceNetworkId() == id }
    if (type.funcs.contains(func)) {
        if (args.size() == 0) {
            debug "Calling ${func} on ${typeName} ${id}"
            child?."${func}"()
        }
        else {
            debug "Calling ${func} on ${typeName} ${id} with args ${args}"
            child?."${func}"(*args)
        }
    }
    else {
        warn "Function ${func} is not supported for type ${typeName}"
    }
}

void handler(evt) {
    def debugString = "Received ${evt.name} event (${evt.value}) from ${evt.device}"
    if( evt.descriptionText ) {
        debugString += " (${evt.descriptionText})"
    }
    debug debugString
    def root = getRootDevice()
    def source = evt.device
    DeviceTypes.findAll{ it.properties.contains(evt.name) }.each {
        def deviceType = it
        root.parse(
            [[
                id: source.getDeviceNetworkId(),
                type: deviceType,
                name: source.getLabel() ?: source.getName(),
                properties: [[
                    name: evt.name,
                    value: evt.value,
                    descriptionText: evt.descriptionText
                ]]
            ]]
        )
    }
}

void debug(String msg) {
    if( debugSpew ) {
        log.debug msg
    }
}

void warn(String msg) {
    log.warn msg
}

Boolean myDevice(id) {
    return id && id.startsWith("Filtered-${app.id}-")
}

def getDeviceTypes() {
    return DeviceTypes
}
