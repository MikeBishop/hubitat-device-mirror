/*
    Filtered Device Mirror
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/

metadata {
    definition (
        name: "Filtered Devices",
        namespace: "evequefou",
        author: "Mike Bishop",
        // importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-teslamate/main/hubitat-teslamate.groovy"
    ) {

    }
    preferences {
        capability "Initialize"
        capability "Refresh"
    }
}

void initialize() {}

void refresh() {
    log.debug "Refreshing"
    parent.refresh()
}

void componentRefresh(child) {
    def childId = child.getDeviceNetworkId().minus("${device.deviceNetworkId}-").minus("-Presence")
    parent.refresh(childId)
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List description) {
    description.each {
        log.debug "Processing ${it}"
        def rootId = device.deviceNetworkId
        def childId = "${rootId}-${it.id}-${it.type}"
        def childLabel = "${it.name} ${it.type}"
        def cd = getChildDevice(childId)
        if (!cd) {
            // Child device doesn't exist; need to create it.
            cd = addChildDevice("hubitat", "Generic Component Presence Sensor", childId, [isComponent: true])
            log.debug "Creating ${childLabel} (${childId})"
        }
        if (cd.getLabel() != childLabel ) {
            cd.setLabel("${it.name} ${it.type}")
        }
        switch(it.type) {
            case "Presence":
                cd.parse(
                    [[name: "presence", value: it.presence, descriptionText:"${it.name} is ${it.presence}"]]
                )
            break
        }
    }
}

void removeChildrenExcept(String property, List childIds) {
    def staleDevices = getChildDevices().
        findAll
        {
            it.getDeviceNetworkId().endsWith("-${property}") &&
                !childIds.contains(
                    it.getDeviceNetworkId().
                        minus("${device.deviceNetworkId}-").
                        minus("-${property}")
                )
        }
    if (staleDevices.any()) {
        log.info "Removing ${staleDevices}, which are no longer selected"
        staleDevices.each { deleteChildDevice(it.getDeviceNetworkId()) }
    }

}