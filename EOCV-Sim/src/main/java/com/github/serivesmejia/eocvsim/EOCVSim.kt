package com.github.serivesmejia.eocvsim

import com.github.serivesmejia.eocvsim.config.ConfigManager
import com.github.serivesmejia.eocvsim.gui.Visualizer
import com.github.serivesmejia.eocvsim.input.InputSourceManager
import com.github.serivesmejia.eocvsim.pipeline.PipelineManager
import com.github.serivesmejia.eocvsim.tuner.TunerManager

import com.github.serivesmejia.eocvsim.util.Log
import com.github.serivesmejia.eocvsim.util.SysUtil
import com.github.serivesmejia.eocvsim.util.event.EventHandler
import com.github.serivesmejia.eocvsim.util.fps.FpsCounter
import com.github.serivesmejia.eocvsim.util.fps.FpsLimiter

import nu.pattern.OpenCV

class EOCVSim(val params: Parameters = Parameters()) {

    companion object {
        @JvmField val VERSION = "2.1.0"
        @JvmField val DEFAULT_EOCV_WIDTH = 320
        @JvmField val DEFAULT_EOCV_HEIGHT = 240

        @Volatile private var alreadyInitializedOnce = false
    }

    @JvmField val onMainUpdate = EventHandler("OnMainUpdate")

    @JvmField val visualizer = Visualizer(this)

    @JvmField val configManager = ConfigManager()
    @JvmField val inputSourceManager = InputSourceManager(this)
    lateinit var pipelineManager: PipelineManager //we'll initialize pipeline manager after loading native lib

    @JvmField var tunerManager = TunerManager(this)

    val fpsLimiter = FpsLimiter(30)
    val fpsCounter = FpsCounter()

    enum class DestroyReason {
        USER_REQUESTED, THEME_CHANGING, RESTART
    }

    fun init() {

        Log.info("EOCVSim", "Initializing EasyOpenCV Simulator v$VERSION")
        Log.white()

        //loading native lib only once in the app runtime
        if (!alreadyInitializedOnce) {
            Log.info("EOCVSim", "Loading native lib...")
            try {
                OpenCV.loadLocally()
                Log.info("EOCVSim", "Successfully loaded native lib")
            } catch (ex: Throwable) {
                Log.error("EOCVSim", "Failure loading native lib", ex)
                Log.info("EOCVSim", "Retrying with old method...")
                SysUtil.loadCvNativeLib()
            }
            Log.white()
        }

        alreadyInitializedOnce = true

        pipelineManager = PipelineManager(this)

        configManager.init() //load config

        visualizer.initAsync(configManager.config.simTheme) //create gui in the EDT
        inputSourceManager.init() //loading user created input sources
        pipelineManager.init() //init pipeline manager (scan for pipelines)
        tunerManager.init() //init tunable variables manager

        visualizer.waitForFinishingInit()

        visualizer.updateSourcesList() //update sources and pick first one
        visualizer.sourceSelector.selectedIndex = 0
        visualizer.updatePipelinesList() //update pipelines and pick first one (DefaultPipeline)
        visualizer.pipelineSelector.selectedIndex = 0

        beginLoop()

    }

    private fun beginLoop() {

        Log.info("EOCVSim", "Begin EOCVSim loop")
        Log.white()

        inputSourceManager.inputSourceLoader.saveInputSourcesToFile()

        while (!Thread.interrupted()) {

            val telemetry = pipelineManager.currentTelemetry

            //run all pending requested runnables
            onMainUpdate.run()

            updateVisualizerTitle()

            inputSourceManager.update(pipelineManager.isPaused)
            tunerManager.update()

            //if we don't have a mat from the inputsource, we'll just skip this frame.
            if (inputSourceManager.lastMatFromSource == null || inputSourceManager.lastMatFromSource.empty()) continue

            try {

                //actually updating the pipeline
                //if paused, it will simply run the pending update listeners
                pipelineManager.update(inputSourceManager.lastMatFromSource)

                //when not paused, post the last pipeline mat to the viewport
                if (!pipelineManager.isPaused)
                    visualizer.viewport.postMat(pipelineManager.lastOutputMat)

                if (telemetry != null) {
                    telemetry.errItem.caption = ""
                    telemetry.errItem.setValue("")
                }

            } catch (ex: Exception) {
                Log.error("Error while processing pipeline", ex)
                if (telemetry != null) {
                    telemetry.errItem.caption = "[/!\\]"
                    telemetry.errItem.setValue("Error while processing pipeline\nCheck console for details.")
                    telemetry.update()
                }
            }

            //updating displayed telemetry
            visualizer.updateTelemetry(pipelineManager.currentTelemetry)

            if (!pipelineManager.isPaused) fpsCounter.update()

            //limit FPS
            try {
                fpsLimiter.sync()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }

        }

        Log.warn("EOCVSim", "Main thread interrupted (" + Integer.toHexString(hashCode()) + ")")

    }

    fun destroy(reason: DestroyReason) {
        val hexCode = Integer.toHexString(this.hashCode())

        Log.warn("EOCVSim", "Destroying current EOCVSim ($hexCode) due to $reason")
        Log.info("EOCVSim", "Trying to save config file...")

        configManager.saveToFile()
        visualizer.close()

        Thread.currentThread().interrupt()
    }

    fun destroy() {
        destroy(DestroyReason.USER_REQUESTED)
    }

    fun restart() {
        Log.info("EOCVSim", "Restarting...")

        Log.white()
        destroy(DestroyReason.RESTART)
        Log.white()

        Thread({ EOCVSim().init() }, "main").start() //run next instance on a separate thread for the old one to get interrupted and ended
    }

    private fun updateVisualizerTitle() {

        val pipelineFpsMsg = " (" + fpsCounter.fps + " Pipeline FPS)"
        val posterFpsMsg = " (" + visualizer.viewport.matPoster.fpsCounter.fps + " Poster FPS)"
        val isPaused = if (pipelineManager.isPaused) " (Paused)" else ""
        val memoryMsg = " (" + SysUtil.getMemoryUsageMB() + " MB Java memory used)"

        val msg = pipelineFpsMsg + posterFpsMsg + isPaused + memoryMsg

        if (pipelineManager.currentPipeline == null) {
            visualizer.setTitleMessage("No pipeline$msg")
        } else {
            visualizer.setTitleMessage(pipelineManager.currentPipelineName + msg)
        }

    }

    class Parameters {
        var scanForPipelinesIn = "org.firstinspires"
    }

}