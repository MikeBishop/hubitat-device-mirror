/*
    Filtered Device Mirror
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/
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
            input "presenceSensors", "capability.presenceSensor", title: "Presence devices to mirror", required: true, multiple: true
        }
        section("Settings") {
            input "debugSpew", "bool", title: "Log debug events", defaultValue: false
        }
    }
}

private createParentDevice() {
    def parentDevice = getParentDevice()
    if (!parentDevice) {
        String dni = "Filtered-" + app.id.toString()
        debug "creating Filtered Device: ${dni}"
        def device = addChildDevice("evequefou", "Filtered Devices", dni, null,
             [name: "Filtered Devices", label: "Filtered Devices", completedSetup: true ])
    } else {
        parentDevice.initialize()
    }
    return parentDevice
}

def getParentDevice() {
   def deviceIdStr = null
   def device
   if (state.childDeviceId) {
      deviceIdStr = state.childDeviceId
      device = getChildDevice(deviceIdStr)
   }
   if (!device) {
      def devices = getChildDevices()
      if (devices.size() > 0) {
          deviceIdStr = getChildDevices().first().getDeviceNetworkId()
          state.childDeviceId = deviceIdStr
          device = getChildDevice(deviceIdStr)
      }
   }
   return device
}

void updated() {
	unsubscribe()
	initialize()
}

void installed() {
	initialize()
}

void initialize() {
    def validPresenceSensors = presenceSensors.findAll { !myDevice(it.getDeviceNetworkId) }
    subscribe(presenceSensors, "presence", handler)
    def presenceIds = presenceSensors*.getDeviceNetworkId()
    presenceIds.each { refresh(it) }
    getParentDevice().removeChildrenExcept("Presence", presenceIds)
}

void refresh() {
    initialize()
}

void refresh(id) {
    def root = getParentDevice()
    debug "Refreshing ${id}"
    if( myDevice(id) ) {
        return
    }
    if (root) {
        def source = presenceSensors.find { it.getDeviceNetworkId() == id }
        if (source) {
            def currentState = [
                        id: id,
                        name: source.getLabel() ?: source.getName(),
                        type: "Presence",
                        presence: source.currentValue("presence")
                    ]
            debug "Mirroring ${source} as ${currentState}"
            root.parse([currentState])
        }
        else {
            log.warn "Sensor ${id} not found!"
        }
    }

}

void handler(evt) {
    debug "Received ${evt}"
    refresh(evt.device.getDeviceNetworkId())
}

void debug(String msg) {
    if( debugSpew ) {
        log.debug msg
    }
}

Boolean myDevice(id) {
    return id && id.startsWith("Filtered-${app.id}-")
}