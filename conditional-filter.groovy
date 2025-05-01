/*
    Conditional Filter
    Copyright 2025 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field

definition (
    parent: "evequefou:Filtered Device Mirror",
    name: "Conditional Filtered Device",
    namespace: "evequefou",
    author: "Mike Bishop",
    description: "Mirror one capability of a complex device with conditional filtering",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-device-mirror/main/conditional-filter.groovy",
    category: "Lighting",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

Map mainPage() {
    dynamicPage(name: "mainPage", title: "Conditional Filtered Device Mirror", install: true, uninstall: true) {
        section("General") {
            input "thisName", "text", title: "Name this Conditional Mirror", required: true, defaultValue: app.getLabel(), submitOnChange: true
            if(thisName) app.updateLabel("$thisName")
        }
        section("Device Selection") {
            input "sourceCapability", "enum",
                options: parent.getDeviceTypes().collectEntries { [(it.capability): it.type] },
                title: "Capability to mirror", required: true, multiple: false, submitOnChange: true
            if (sourceCapability) {
                input "sourceDevice", sourceCapability, title: "Device to mirror", required: true, multiple: false
            }

            input "conditionDevice", "capability.*",
                title: "Device to filter with", required: true, multiple: false, submitOnChange: true
            if (conditionDevice) {
                def acceptableAttributes = conditionDevice.getSupportedAttributes().findAll{ ["ENUM"].contains(it.dataType) };
                if (acceptableAttributes.size() > 1) {
                    input "conditionAttributeName", "enum",
                        options: acceptableAttributes.collect{ it.name },
                        title: "Attribute to filter with", required: true, multiple: false, submitOnChange: true
                } else if (acceptableAttributes.size() == 1) {
                    app.updateSetting("conditionAttributeName", acceptableAttributes[0].name)
                }
                else {
                    log.warn "Condition device ${conditionDevice} has no acceptable attributes!"
                    paragraph "Condition device ${conditionDevice} has no acceptable attributes!"
                    app.clearSetting("conditionAttributeName");
                }
            }
        }
        if( sourceDevice && conditionDevice && conditionAttributeName ) {
            def properties = parent.getDeviceTypes().find { it.capability == sourceCapability }.properties
            properties.each { prop ->
                section("How to set ${prop}") {
                    def conditionAttribute = conditionDevice.getSupportedAttributes().find { it.name == conditionAttributeName }
                    def currentValue = conditionDevice.currentValue(conditionAttribute.name)

                    def sourceAttribute = sourceDevice.getSupportedAttributes().find{ it.name == prop }
                    switch( conditionAttribute.dataType ) {
                        case "ENUM":
                            def options = conditionAttribute.getValues()
                            for( def opt in options ) {
                                buildSelector(sourceAttribute, opt, opt)
                            }
                            break
                        case "NUMBER":
                            // Array of splitpoints; selectors for
                            // - x < split1
                            // - split1 <= x < split2
                            // ...
                            // - x >= splitN

                            // Ability to add splitpoint
                            // Ability to delete splitpoint (only if multiple)
                        case "STRING":
                            // List of possible values and selector for each
                            // Ability to add new value
                            // Selector for non-matching values
                        default:
                            paragraph "Unsupported data type ${conditionAttribute.dataType} for ${conditionAttribute.name}"
                            break
                    }
                }
            }
        }
        section("Settings") {
            input "debugSpew", "bool", title: "Log debug events", defaultValue: false
        }
    }
}

private buildSelector(sourceAttribute, conditionValue, displayConditionValue) {
    log.debug "buildSelector: ${sourceAttribute} ${conditionValue} ${displayConditionValue}"
    log.debug "sourceAttribute: ${sourceAttribute.inspect()}"

    input "conditionValue_${sourceAttribute}_for_${conditionValue}", "enum", options: BEHAVIORS,
        title: "When filter ${conditionAttribute} is ${conditionValue}, use",
        defaultValue: "source", submitOnChange: true, required: true

    switch(settings["conditionValue_${sourceAttribute}_for_${conditionValue}"]) {
        case "fixed":
            // Same 3 cases as above, but now for the source attribute
            switch(sourceAttribute.dataType) {
                case "NUMBER":
                    input "conditionValue_${sourceAttribute}_for_${conditionValue}_fixed", "number",
                        title: "What value?", required: true, multiple: false
                    break
                case "STRING":
                    input "conditionValue_${sourceAttribute}_for_${conditionValue}_fixed", "text",
                        title: "What value?", required: true
                    break
                case "ENUM":
                    input "conditionValue_${sourceAttribute}_for_${conditionValue}_fixed", "enum",
                        options: sourceAttribute.getValues(),
                        title: "What value?", required: true, multiple: false
                    break
            }
            break

        case "other":
            input "conditionValue_${sourceAttribute}_for_${conditionValue}_other", sourceCapability,
                title: "What device's ${sourceAttribute} to use when ${sourceDevice} is ${displayConditionValue}?",
                required: true, multiple: false
            break
        case "source":
            // No additional input needed
            break
    }

}

@Field static final Map BEHAVIORS = [
    "fixed": "Fixed value",
    "source": "Source device value",
    "other": "Other device value",
]
