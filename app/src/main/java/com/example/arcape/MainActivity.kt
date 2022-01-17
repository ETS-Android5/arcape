package com.example.arcape

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentOnAttachListener
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Sceneform
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.samples.augmentedimages.mqtt.MqttClientHelper
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.BaseArFragment.OnSessionConfigurationListener
import com.google.ar.sceneform.ux.TransformableNode
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer


class MainActivity : AppCompatActivity(), FragmentOnAttachListener, OnSessionConfigurationListener {

    private val mqttClient by lazy {
        MqttClientHelper(this)
    }

    private val futures: MutableList<CompletableFuture<Void>> = ArrayList()
    private var arFragment: ArFragment? = null
    private var puzzle1Detected = false
    private var puzzle2Detected = false
    private var puzzle3Detected = false
    private var puzzle4Detected = false
    private var puzzle5Detected = false
    private var database: AugmentedImageDatabase? = null

    var puzzle1Sub = 0
    var puzzle2Sub = 0
    var puzzle3Sub = 0
    var puzzle4Sub = 0
    var puzzle5Sub = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        setMqttCallBack()


        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { _: View?, insets: WindowInsetsCompat ->
            (toolbar.layoutParams as MarginLayoutParams).topMargin = insets
                .getInsets(WindowInsetsCompat.Type.systemBars()).top
            WindowInsetsCompat.CONSUMED
        }

        supportFragmentManager.addFragmentOnAttachListener(this)
        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                supportFragmentManager.beginTransaction()
                    .add(R.id.arFragment, ArFragment::class.java, null)
                    .commit()
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        this.finish()
    }

    private fun setMqttCallBack() {
        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(b: Boolean, s: String) {
                Log.w("Connected","Successfully Connected")
            }
            override fun connectionLost(throwable: Throwable) {
                Log.w("Disconnected","Connection Lost")
            }
            @Throws(Exception::class)
            override fun messageArrived(topic: String, mqttMessage: MqttMessage) {
                Log.w("Debug", "Message received from host '$MQTT_HOST': $mqttMessage")
                if(topic == "test/puzzle1")
                {
                    when("$mqttMessage")
                    {
                        "Not Activated" -> puzzle1Sub = 0
                        "Activated" -> puzzle1Sub = 1
                        "Solved" -> puzzle1Sub = 2
                    }
                }
                if(topic == "test/puzzle2")
                {
                    when("$mqttMessage")
                    {
                        "Not Activated" -> puzzle2Sub = 0
                        "Activated" -> puzzle2Sub = 1
                        "Solved" -> puzzle2Sub = 2
                    }
                }
                if(topic == "test/puzzle3")
                {
                    when("$mqttMessage")
                    {
                        "Not Activated" -> puzzle3Sub = 0
                        "Activated" -> puzzle3Sub = 1
                        "Solved" -> puzzle3Sub = 2
                    }
                }
                if(topic == "test/puzzle4")
                {
                    when("$mqttMessage")
                    {
                        "Not Activated" -> puzzle4Sub = 0
                        "Activated" -> puzzle4Sub = 1
                        "Solved" -> puzzle4Sub = 2
                    }
                }
                if(topic == "test/puzzle5")
                {
                    when("$mqttMessage")
                    {
                        "Not Activated" -> puzzle5Sub = 0
                        "Activated" -> puzzle5Sub = 1
                        "Solved" -> puzzle5Sub = 2
                    }
                }
            }
            override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {
                Log.w("Debug", "Message published to host '$MQTT_HOST'")
            }
        })
    }

    override fun onAttachFragment(fragmentManager: FragmentManager, fragment: Fragment) {
        if (fragment.id == R.id.arFragment) {
            arFragment = fragment as ArFragment
            arFragment!!.setOnSessionConfigurationListener(this)
        }
    }

    override fun onSessionConfiguration(session: Session, config: Config) {
        // Disable plane detection
        config.planeFindingMode = Config.PlaneFindingMode.DISABLED

        //Using preloaded Image Database arcoreimg tool
        val inputStream = assets.open("myimages5.imgdb")
        database= AugmentedImageDatabase.deserialize(session,inputStream)

        //If you want to generate database on the go
        /*database = AugmentedImageDatabase(session)
        val padlock = BitmapFactory.decodeResource(resources, R.drawable.padlock)
        val robotImage = BitmapFactory.decodeResource(resources, R.drawable.robot)
        database!!.addImage("robot", robotImage)
        database!!.addImage("padlock", padlock)*/

        config.augmentedImageDatabase = database

        // Check for image detection
        arFragment!!.setOnAugmentedImageUpdateListener { augmentedImage: AugmentedImage ->
            onAugmentedImageTrackingUpdate(
                augmentedImage
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttClient.destroy()
        futures.forEach(Consumer { future: CompletableFuture<Void> ->
            if (!future.isDone) future.cancel(
                true
            )
        })
    }

    private fun placeObject(anchorNode: AnchorNode, string: String){
        anchorNode.worldScale = Vector3(0.1f, 0.1f, 0.1f)
        arFragment!!.arSceneView.scene.addChild(anchorNode)
        futures.add(ModelRenderable.builder()
            .setSource(this, Uri.parse(string))
            .setIsFilamentGltf(true)
            .build()
            .thenAccept { textModel: ModelRenderable? ->
                val modelNode = TransformableNode(
                    arFragment!!.transformationSystem
                )
                modelNode.renderable = textModel
                anchorNode.addChild(modelNode)
            }
            .exceptionally {
                Toast.makeText(
                    this,
                    "Unable to load rabbit model",
                    Toast.LENGTH_LONG
                )
                    .show()
                null
            })
    }

    private fun onAugmentedImageTrackingUpdate(augmentedImage: AugmentedImage) {
        if (puzzle1Detected && puzzle2Detected && puzzle3Detected && puzzle4Detected && puzzle5Detected) {
            return
        }
        if (augmentedImage.trackingState == TrackingState.TRACKING ) {
            val anchorNodePuzzle1 = AnchorNode(augmentedImage.createAnchor(augmentedImage.centerPose))
            val anchorNodePuzzle2 = AnchorNode(augmentedImage.createAnchor(augmentedImage.centerPose))
            val anchorNodePuzzle3 = AnchorNode(augmentedImage.createAnchor(augmentedImage.centerPose))
            val anchorNodePuzzle4 = AnchorNode(augmentedImage.createAnchor(augmentedImage.centerPose))
            val anchorNodePuzzle5 = AnchorNode(augmentedImage.createAnchor(augmentedImage.centerPose))
            if (!puzzle1Detected && augmentedImage.name == "puzzle1") {
                puzzle1Detected = true
                val topic = "test/puzzle1"
                mqttClient.subscribe(topic)
                when(puzzle1Sub)
                {
                    0 -> placeObject(anchorNodePuzzle1, "models/nactive.glb")
                    1 -> placeObject(anchorNodePuzzle1, "models/hint.glb")
                    2 -> placeObject(anchorNodePuzzle1, "models/solved.glb")
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    arFragment!!.arSceneView.scene.removeChild(anchorNodePuzzle1)
                    puzzle1Detected = false
                }, 1000)
            }
            if (!puzzle2Detected && augmentedImage.name == "puzzle2") {
                puzzle2Detected = true
                val topic = "test/puzzle2"
                mqttClient.subscribe(topic)
                when(puzzle2Sub)
                {
                    0 -> placeObject(anchorNodePuzzle2, "models/nactive.glb")
                    1 -> placeObject(anchorNodePuzzle2, "models/hint.glb")
                    2 -> placeObject(anchorNodePuzzle2, "models/solved.glb")
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    arFragment!!.arSceneView.scene.removeChild(anchorNodePuzzle2)
                    puzzle2Detected = false
                }, 1000)
            }
            if (!puzzle3Detected && augmentedImage.name == "puzzle3") {
                puzzle3Detected = true
                val topic = "test/puzzle3"
                mqttClient.subscribe(topic)
                when(puzzle3Sub)
                {
                    0 -> placeObject(anchorNodePuzzle3, "models/nactive.glb")
                    1 -> placeObject(anchorNodePuzzle3, "models/hint.glb")
                    2 -> placeObject(anchorNodePuzzle3, "models/solved.glb")
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    arFragment!!.arSceneView.scene.removeChild(anchorNodePuzzle3)
                    puzzle3Detected = false
                }, 1000)
            }
            if (!puzzle4Detected && augmentedImage.name == "puzzle4") {
                puzzle4Detected = true
                val topic = "test/puzzle4"
                mqttClient.subscribe(topic)
                when(puzzle4Sub)
                {
                    0 -> placeObject(anchorNodePuzzle4, "models/nactive.glb")
                    1 -> placeObject(anchorNodePuzzle4, "models/hint.glb")
                    2 -> placeObject(anchorNodePuzzle4, "models/solved.glb")
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    arFragment!!.arSceneView.scene.removeChild(anchorNodePuzzle4)
                    puzzle4Detected = false
                }, 1000)
            }
            if (!puzzle5Detected && augmentedImage.name == "puzzle5") {
                puzzle5Detected = true
                val topic = "test/puzzle5"
                mqttClient.subscribe(topic)
                when(puzzle5Sub)
                {
                    0 -> placeObject(anchorNodePuzzle5, "models/nactive.glb")
                    1 -> placeObject(anchorNodePuzzle5, "models/hint.glb")
                    2 -> placeObject(anchorNodePuzzle5, "models/solved.glb")
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    arFragment!!.arSceneView.scene.removeChild(anchorNodePuzzle5)
                    puzzle5Detected = false
                }, 1000)
            }
        }
    }
}
