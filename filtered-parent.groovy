/*
    Filtered Device Mirror
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field

metadata {
    definition (
        name: "Filtered Devices",
        namespace: "evequefou",
        author: "Mike Bishop",
        importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-device-mirror/main/filtered-parent.groovy"
    ) {
        capability "Initialize"
        capability "Refresh"
    }
    preferences {
        input(
            name: "debugLogging",
            type: "bool",
            title: "Enable debug logging",
            required: false,
            default: false
        )
    }
}

void initialize() {}

void refresh() {
    debug "Refreshing"
    parent.refresh()
}

void componentInvoke(child, targetMethod, args = []) {
    // void mirrorFunc(def type, def id, def func, def... args) {
    log.debug "componentInvoke: ${child} ${targetMethod} ${args}"
    def childId = child.getDeviceNetworkId();
    def type = parent.getDeviceTypes().find { childId.endsWith("-${it.type}")}
    def baseId = childId.minus("${device.deviceNetworkId}-").minus("-${type.type}");

    if (type) {
        log.debug "calling mirrorFunc with ${type.type}, ${baseId}, ${targetMethod}, ${args}"
        parent.mirrorFunc(
            type.type,
            baseId,
            targetMethod,
            *args
        );
    }
}

void componentRefresh(child) {
    def childId = child.getDeviceNetworkId()
    def type = parent.getDeviceTypes().find { childId.endsWith("-${it.type}")}
    if (type) {
        parent.refresh(
            childId.minus("${device.deviceNetworkId}-").minus("-${type.type}"),
            type.properties
        )
    }
}

void componentOn(child) {
    debug "componentOn: ${child}"
    componentInvoke(child, "on");
}

void componentOff(child) {
    debug "componentOff: ${child}"
    componentInvoke(child, "off");
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List description) {
    description.each {
        debug "Processing ${it}"
        def rootId = device.deviceNetworkId
        def childId = "${rootId}-${it.id}-${it.type.type}"
        def childLabel = "${it.name} ${it.type.type}"
        def cd = getChildDevice(childId)
        if (!cd) {
            // Child device doesn't exist; need to create it.
            debug "Creating ${childLabel} (${childId})"
            cd = addChildDevice(it.type.namespace, it.type.driver, childId, [isComponent: true])
        }
        else {
            debug "${childId} is ${cd}"
        }
        if (cd.getLabel() != childLabel ) {
            cd.setLabel(childLabel)
        }
        cd.parse(it.properties)
    }
}

void removeChildrenExcept(String property, List childIds) {
    def staleDevices = getChildDevices().findAll
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

def debug(msg) {
	if (debugLogging) {
    	log.debug msg
    }
}
