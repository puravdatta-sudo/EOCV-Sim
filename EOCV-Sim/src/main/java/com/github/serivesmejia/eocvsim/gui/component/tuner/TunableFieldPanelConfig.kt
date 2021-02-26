/*
 * Copyright (c) 2021 Sebastian Erives
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.github.serivesmejia.eocvsim.gui.component.tuner

import com.github.serivesmejia.eocvsim.EOCVSim
import com.github.serivesmejia.eocvsim.gui.component.input.EnumComboBox
import com.github.serivesmejia.eocvsim.gui.component.input.SizeFields
import com.github.serivesmejia.eocvsim.tuner.TunableField
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JToggleButton

class TunableFieldPanelConfig(private val fieldOptions: TunableFieldPanelOptions,
                              private val eocvSim: EOCVSim) : JPanel() {

    var config = eocvSim.config.globalTunableFieldsConfig.copy()
        private set

    var appliedSpecificConfig = false
        private set

    private val sliderRangeFields     = SizeFields(config.sliderRange, allowsDecimals, true,"Slider range:", " to ")
    private val colorSpaceComboBox    = EnumComboBox("Color space: ", PickerColorSpace::class.java, PickerColorSpace.values())

    private val applyToAllButtonPanel = JPanel(GridBagLayout())
    private val applyToAllButton      = JToggleButton("Apply to all fields...")

    private val applyModesPanel             = JPanel(GridLayout(1, 2))
    private val applyToAllFieldsButton      = JButton("Globally")
    private val applyToAllOfSameTypeButton  = JButton("Of same type")

    private val constCenterBottom = GridBagConstraints()

    private val allowsDecimals
        get() = fieldOptions.fieldPanel.tunableField.allowMode == TunableField.AllowMode.ONLY_NUMBERS_DECIMAL

    enum class PickerColorSpace(val cvtCode: Int) {
        YCrCb(Imgproc.COLOR_RGB2YCrCb),
        HSV(Imgproc.COLOR_RGB2HSV),
        RGB(Imgproc.COLOR_RGBA2RGB),
        Lab(Imgproc.COLOR_RGB2Lab)
    }

    data class Config(var sliderRange: Size,
                      var pickerColorSpace: PickerColorSpace)

    init {
        layout = GridLayout(3, 1)

        //handle slider range changes
        sliderRangeFields.onChange.doPersistent {
            if(sliderRangeFields.valid) {
                try {
                    config.sliderRange = sliderRangeFields.currentSize
                } catch(ignored: NumberFormatException) {}
            }
        }
        add(sliderRangeFields)

        //combo box to select color space
        colorSpaceComboBox.selectedEnum = config.pickerColorSpace
        add(colorSpaceComboBox)

        //centering apply to all button...
        val constCenter    = GridBagConstraints()
        constCenter.anchor = GridBagConstraints.CENTER
        constCenter.fill   = GridBagConstraints.HORIZONTAL
        constCenter.gridy  = 0

        //add apply to all button to a centered pane
        applyToAllButtonPanel.add(applyToAllButton, constCenter)
        add(applyToAllButtonPanel)

        //display or hide apply to all mode buttons
        applyToAllButton.addActionListener { toggleApplyModesPanel(applyToAllButton.isSelected) }

        //apply globally button and disable toggle for apply to all button
        applyToAllFieldsButton.addActionListener {
            toggleApplyModesPanel(false)
            applyGlobally()
        }
        applyModesPanel.add(applyToAllFieldsButton)

        //apply of same type button and disable toggle for apply to all button
        applyToAllOfSameTypeButton.addActionListener {
            toggleApplyModesPanel(false)
            applyOfSameType()
        }
        applyModesPanel.add(applyToAllOfSameTypeButton)

        //add two apply to all modes buttons to the bottom center
        constCenterBottom.anchor = GridBagConstraints.CENTER
        constCenterBottom.fill = GridBagConstraints.HORIZONTAL
        constCenterBottom.gridy = 1

        applyToAllButtonPanel.add(applyModesPanel, constCenterBottom)

        applyFromConfig()
    }

    //hides or displays apply to all mode buttons
    private fun toggleApplyModesPanel(show: Boolean) {
        if(show) {
            applyToAllButtonPanel.add(applyModesPanel, constCenterBottom)
        } else {
            applyToAllButtonPanel.remove(applyModesPanel)
        }

        //toggle or untoggle apply to all button
        applyToAllButton.isSelected = show

        //need to repaint...
        applyToAllButtonPanel.repaint(); applyToAllButtonPanel.revalidate()
        repaint(); revalidate()
    }

    private fun applyGlobally() {
        eocvSim.config.globalTunableFieldsConfig = config
    }

    private fun applyOfSameType() {
        val typeClass = fieldOptions.fieldPanel.tunableField::class.java
        eocvSim.config.specificTunableFieldConfig[typeClass.name] = config;
    }

    //set the current config values and hide apply modes panel when panel show
    fun panelShow() {
        updateGuiFromCurrentConfig()

        applyToAllButton.isSelected = false
        toggleApplyModesPanel(false)
    }

    //set the slider bounds when the popup gets closed
    fun panelHide() {
        //if user entered a valid number and our max value is bigger than the minimum...
        if(sliderRangeFields.valid && config.sliderRange.height > config.sliderRange.width) {
            fieldOptions.fieldPanel.setSlidersRange(config.sliderRange.width, config.sliderRange.height)
        }
        toggleApplyModesPanel(true)
    }

    fun applyFromConfig() {
        val typeClass = fieldOptions.fieldPanel.tunableField::class.java
        val specificConfigs = eocvSim.config.specificTunableFieldConfig

        //apply specific config if we have one, or else, apply global
        config = if(specificConfigs.containsKey(typeClass.name)) {
            appliedSpecificConfig = true
            specificConfigs[typeClass.name]!!
        } else {
            eocvSim.config.globalTunableFieldsConfig
        }

        updateGuiFromCurrentConfig()
    }

    fun updateGuiFromCurrentConfig() {
        sliderRangeFields.widthTextField.text  = config.sliderRange.width.toString()
        sliderRangeFields.heightTextField.text = config.sliderRange.height.toString()

        colorSpaceComboBox.selectedEnum        = config.pickerColorSpace
    }

}