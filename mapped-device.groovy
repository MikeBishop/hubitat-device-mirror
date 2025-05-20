/*
    Mapped Device Filter
    Copyright 2025 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field

definition (
    parent: "evequefou:Filtered Device Mirror",
    name: "Mapped Device",
    namespace: "evequefou",
    author: "Mike Bishop",
    description: "Mirror one capability of a device with mapping logic",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-device-mirror/main/mapped-device.groovy",
    category: "Lighting",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

Map mainPage() {
    dynamicPage(name: "mainPage", title: "Mapped Device Mirror", install: true, uninstall: true) {
        section("General") {
            input "thisName", "text", title: "Name this Mapped Device", required: true, defaultValue: app.getLabel(), submitOnChange: true
            if(thisName) app.updateLabel("$thisName")
        }
        section("Device Selection") {
            input "outputCapability", "enum",
                options: parent.getDeviceTypes().collectEntries { [(it.capability): it.type] },
                title: "Capability to produce", required: true, multiple: false, submitOnChange: true

            [["first", true], ["second", false]].each { device ->
                def devicePrefix = device[0]
                def deviceKey = "${device[0]}Device"
                def deviceLabel = "${device[0].capitalize()} input device"
                def required = device[1]

                input deviceKey, "capability.*", title: deviceLabel, required: required, multiple: false, submitOnChange: true

                if(settings[deviceKey] ) {
                    def inputDevice = settings[deviceKey]
                    def attributeKey = "${devicePrefix}AttributeName"
                    def acceptableAttributes = inputDevice.getSupportedAttributes()?.findAll{ ["ENUM"].contains(it.dataType) };
                    if (acceptableAttributes?.size() > 1) {
                        input attributeKey, "enum",
                            options: acceptableAttributes.collect{ it.name },
                            title: "Attribute to consider", required: true, multiple: false, submitOnChange: true
                    } else if (acceptableAttributes?.size() == 1) {
                        app.updateSetting(attributeKey, acceptableAttributes[0].name)
                        paragraph "Input device ${inputDevice} has only one usable attribute (${acceptableAttributes[0].name})"
                    }
                    else {
                        log.warn "Input device ${inputDevice} has no acceptable attributes!"
                        paragraph "Input device ${inputDevice} has no acceptable attributes!"
                        app.clearSetting(attributeKey);
                    }
                }
            }
        }
        if( firstDevice && firstAttributeName && outputCapability ) {
            def properties = parent.getDeviceTypes().find { it.capability == outputCapability }.properties
            properties.each { prop ->
                section("How to set ${prop}") {
                    def firstAttribute = firstDevice.getSupportedAttributes().find { it.name == firstAttributeName }
                    def secondAttribute = secondDevice?.getSupportedAttributes()?.find { it.name == secondAttributeName }
                    def outputAttribute = getChildDevice().getSupportedAttributes().find { it.name == prop }

                    /*
                        TODO: Non-enum types will come later

                        If the inputs are enums, just build a selector for each combination.
                        If a given device is a number, first have splitpoints,
                        then build a selector for each range.
                        If a given device is a string, assemble a list of
                        possible values and build a selector for each, plus
                        "anything else"

                        If outputs are strings, have a text box (support
                        variables?)
                        If outputs are numbers, support:
                        - Fixed
                        - Variable plus fixed offset
                        - Input (if number) plus fixed/variable/other-input offset
                        */

                    def firstValues = firstAttribute.getValues()
                    def secondValues = secondAttribute?.getValues() ?: [null]
                    def outputValues = outputAttribute.getValues() + [UNCHANGED];

                    for (def firstValue in firstValues) {
                        int numOptions = Math.max(secondValues.size(), 1);
                        int width = Math.max(Math.floor(12.0 / numOptions), 1);

                        for (def secondValue in secondValues) {
                            def heading = "When ${firstDevice} ${firstAttribute.name} is ${firstValue}"
                            if( secondValue != null ) {
                                heading += " and ${secondDevice} ${secondAttribute.name} is ${secondValue}"
                            }
                            heading += "..."

                            input constructKey(prop, firstValue, secondValue), "enum", options: outputValues, width: width,
                                title: heading, defaultValue: false, submitOnChange: true, required: true
                        }
                    }

                            // Array of splitpoints; selectors for
                            // - x < split1
                            // - split1 <= x < split2
                            // ...
                            // - x >= splitN

                            // Ability to add splitpoint
                            // Ability to delete splitpoint (only if multiple)
                            // List of possible values and selector for each
                            // Ability to add new value
                            // Selector for non-matching values
                }
            }
        }
    }
}

void updated() {
	unsubscribe()
    cleanup()
	initialize()
}

void cleanup() {
    def firstAttribute = firstDevice?.getSupportedAttributes()?.find { it.name == firstAttributeName }
    def firstValues = firstAttribute?.getValues() ?: []
    def secondAttribute = secondDevice?.getSupportedAttributes()?.find { it.name == secondAttributeName }
    def secondValues = secondAttribute?.getValues() ?: [null]
    def outputProperties = parent.getDeviceTypes().find { it.capability == outputCapability }.properties

    def goodKeys = outputProperties.collect { out ->
        firstValues.collect { it1 ->
            secondValues.collect { it2 ->
                constructKey(out, it1, it2)
            }
        }
    }.flatten()

    debug "Good keys: ${goodKeys}"

    def toRemove = settings.keySet().findAll { it.startsWith("condition_")} - goodKeys
    debug "Removing keys: ${toRemove}"

    toRemove.each { app.clearSetting(it) }
}

void installed() {
	initialize()
}

void uninstalled() {
    parent.deleteChildDevice(getChildDeviceId())
}

private getChildDeviceId() {
    return "Filtered-" + app.id.toString()
}

private getChildDevice() {
    def type = parent.getDeviceTypes().find { it.capability == outputCapability }
    return parent.fetchChildDevice(getChildDeviceId(), thisName, type.namespace, type.driver)
}

void initialize() {
    [[firstDevice,firstAttributeName], [secondDevice,secondAttributeName]].each {
        def device = it[0];
        def attribute = it[1];
        debug "Subscribing to ${device} ${attribute}"

        if( device && attribute ) {
            subscribe(device, attribute, "updateState")
        }
    }
    updateState();
}

void updateState(evt = null) {
    def source = evt?.device;
    def property = evt?.name;
    def value = evt?.value;
    def description = evt?.descriptionText

    if( evt ) {
        debug "Received ${property} ${value} from ${source} ${description ?: ""}"
        description = description ?: " ${source} ${property} became ${value}"
    }

    def firstValue = firstDevice?.currentValue(firstAttributeName);
    def secondValue = secondDevice?.currentValue(secondAttributeName);
    def outputProperties = parent.getDeviceTypes().find { it.capability == outputCapability }.properties

    if ( firstValue != null ) {
        if( firstDevice && firstAttributeName && outputCapability ) {
            def properties = parent.getDeviceTypes().find { it.capability == outputCapability }.properties
            getChildDevice().parse(outputProperties.collect {
                [
                    name: it,
                    value: settings[constructKey(it, firstValue, secondValue)],
                    descriptionText: description
                ]
            }.findAll{ it.value != UNCHANGED && it.value != null }
            );
        }
    }
}

private constructKey(outputAttribute, firstValue, secondValue) {
    def key = "${outputAttribute}-${firstValue}"
    if( secondValue != null ) {
        key += "-${secondValue}"
    }
    return key
}

void refresh() {
    initialize()
}

void debug(String msg) {
    if( parent.debugEnabled() ) {
        log.debug msg
    }
}

@Field static final String UNCHANGED = "(unchanged)"
