package igrek.forceawaken.activity

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import igrek.forceawaken.R
import igrek.forceawaken.alarm.AlarmsConfig
import igrek.forceawaken.alarm.VibratorService
import igrek.forceawaken.info.UiInfoService
import igrek.forceawaken.info.logger.LoggerFactory
import igrek.forceawaken.inject.LazyExtractor
import igrek.forceawaken.inject.LazyInject
import igrek.forceawaken.inject.appFactory
import igrek.forceawaken.layout.CommonLayout
import igrek.forceawaken.layout.navigation.NavigationMenuController
import igrek.forceawaken.persistence.AlarmsPersistenceService
import igrek.forceawaken.ringtone.AlarmPlayerService
import igrek.forceawaken.ringtone.Ringtone
import igrek.forceawaken.ringtone.RingtoneManagerService
import igrek.forceawaken.system.WindowManagerService
import igrek.forceawaken.task.AwakeTask
import igrek.forceawaken.time.AlarmTimeService
import igrek.forceawaken.volume.NoiseDetectorService
import igrek.forceawaken.volume.VolumeCalculatorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import java.io.IOException
import java.util.*

class AwakenActivityLayout(
        activity: LazyInject<Activity> = appFactory.activity,
        windowManagerService: LazyInject<WindowManagerService> = appFactory.windowManagerService,
        navigationMenuController: LazyInject<NavigationMenuController> = appFactory.navigationMenuController,
        uiInfoService: LazyInject<UiInfoService> = appFactory.uiInfoService,
        alarmsPersistenceService: LazyInject<AlarmsPersistenceService> = appFactory.alarmsPersistenceService,
        commonLayout: LazyInject<CommonLayout> = appFactory.commonLayout,
        alarmTimeService: LazyInject<AlarmTimeService> = appFactory.alarmTimeService,
        noiseDetectorService: LazyInject<NoiseDetectorService> = appFactory.noiseDetectorService,
        alarmPlayerService: LazyInject<AlarmPlayerService> = appFactory.alarmPlayerService,
        vibratorService: LazyInject<VibratorService> = appFactory.vibratorService,
        ringtoneManagerService: LazyInject<RingtoneManagerService> = appFactory.ringtoneManagerService,
        volumeCalculatorService: LazyInject<VolumeCalculatorService> = appFactory.volumeCalculatorService,
) {
    private val activity by LazyExtractor(activity)
    private val windowManagerService by LazyExtractor(windowManagerService)
    private val navigationMenuController by LazyExtractor(navigationMenuController)
    private val uiInfoService by LazyExtractor(uiInfoService)
    private val alarmsPersistenceService by LazyExtractor(alarmsPersistenceService)
    private val commonLayout by LazyExtractor(commonLayout)
    private val alarmTimeService by LazyExtractor(alarmTimeService)
    private val noiseDetectorService by LazyExtractor(noiseDetectorService)
    private val alarmPlayer by LazyExtractor(alarmPlayerService)
    private val vibratorService by LazyExtractor(vibratorService)
    private val ringtoneManager by LazyExtractor(ringtoneManagerService)
    private val volumeCalculatorService by LazyExtractor(volumeCalculatorService)

    private val logger = LoggerFactory.logger
    private val random = Random()

    private val wakeUpInfos = arrayOf(
            "RISE AND SHINE, YOU MOTHERFUCKER!!!", "Kill Zombie process!!!",
            "Wstawaj, Nie Pierdol!")
    private var fakeTimeLabel: TextView? = null
    private var wakeUpLabel: TextView? = null

    private val ALARM_VIBRATION_PERIOD: Long = 1000
    private val ALARM_VIBRATION_PWM = 0.5

    private var currentRingtone: Ringtone? = null
    private var ringtoneListAdapter: ArrayAdapter<Ringtone>? = null
    private var awakeTask: AwakeTask? = null
    var activateAlarmTime: DateTime? = null

    fun init() {
        logger.info("Initializing application...")

        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                windowManagerService.hideTaskbar()
                initLayout()
            }

            logger.info("AwakenActivity Layout has been initialized.")
        }
    }

    private fun initLayout() {
        activateAlarmTime = DateTime.now()

        activity.setContentView(R.layout.awaken_main)
        commonLayout.init()
        navigationMenuController.init()

        showFullscreenWhenLocked()
        fakeTimeLabel = activity.findViewById(R.id.fakeTime)
        wakeUpLabel = activity.findViewById(R.id.wakeUpLabel)

        bootstrapAlarm()
    }

    private fun showFullscreenWhenLocked() {
        windowManagerService.setFullscreen(true)
        activity.setShowWhenLocked(true)
        activity.setTurnScreenOn(true)
        val keyguardManager: KeyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(activity, null)
    }

    private fun bootstrapAlarm() {
        val fakeTimeStr: String = alarmTimeService.fakeCurrentTime.toString("HH:mm")
        fakeTimeLabel?.text = fakeTimeStr
        val alarmId = System.currentTimeMillis()
        wakeUpLabel?.text = wakeUpInfos[random.nextInt(wakeUpInfos.size)]
        // measure surrounding loudness level
        noiseDetectorService.measureNoiseLevel(1000, object : NoiseDetectorService.NoiseLevelMeasureCallback {
            override fun onComplete(amplitudeDb: Double) {
                startAlarmPlaying(amplitudeDb, alarmId)
            }
        })
    }


    fun startAlarmPlaying(noiseLevel: Double, alarmId: Long) {
        // stop the previous alarm
        alarmPlayer.stopAlarm()
        scheduleVolumeBoost(alarmId, (120 * 1000).toLong(), 1.2)
        scheduleVolumeBoost(alarmId, (130 * 1000).toLong(), 1.2)
        scheduleVolumeBoost(alarmId, (140 * 1000).toLong(), 1.2)
        scheduleVolumeBoost(alarmId, (150 * 1000).toLong(), 1.5)
        scheduleVolumeBoost(alarmId, (160 * 1000).toLong(), 1.5)
        scheduleVolumeBoost(alarmId, (170 * 1000).toLong(), 1.5)
        Handler().postDelayed({
            if (alarmPlayer.isPlaying && alarmPlayer.alarmId == alarmId) {
                alarmPlayer.volume = 1.0
                logger.info("Alarm is still playing - volume level boosted to " + 1.0)
            }
        }, (180 * 1000).toLong())
        val vibrationsBooster: Runnable = object : Runnable {
            override fun run() {
                if (alarmPlayer.isPlaying && alarmPlayer.alarmId == alarmId) {
                    logger.info("Alarm is still playing - turning on vibrations")
                    vibratorService.vibrate((ALARM_VIBRATION_PWM * ALARM_VIBRATION_PERIOD).toLong())
                    Handler().postDelayed(this, ALARM_VIBRATION_PERIOD)
                }
            }
        }
        Handler().postDelayed(vibrationsBooster, (200 * 1000).toLong())
        try {
            currentRingtone = ringtoneManager.randomRingtone
            logger.info("Current Ringtone: " + currentRingtone!!.name)
            val volume: Double = volumeCalculatorService.calcFinalVolume(noiseLevel)
            logger.info("Alarm volume level: $volume")

            //Uri ringUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.hopes_quiet);
            alarmPlayer.playAlarm(currentRingtone!!.uri, volume)
            alarmPlayer.alarmId = alarmId
        } catch (e: IOException) {
            logger.error(e)
        }
        var ringtones: List<Ringtone?> = ringtoneManager.allRingtones
        ringtones = ringtones.shuffled() // that's the evilest thing i can imagine
        ringtoneListAdapter = ArrayAdapter<Ringtone>(activity, R.layout.list_item, ringtones)
        val listView: ListView = activity.findViewById(R.id.ringtones_answer_list)
        listView.adapter = ringtoneListAdapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { adapter1: AdapterView<*>, v: View?, position: Int, id: Long ->
            val selected = adapter1.getItemAtPosition(position) as Ringtone
            onRingtoneAnswer(selected)
        }
    }

    private fun scheduleVolumeBoost(alarmId: Long, whenMs: Long, volMultiplier: Double) {
        Handler().postDelayed({
            if (alarmPlayer.isPlaying && alarmPlayer.alarmId == alarmId) {
                val newVol: Double = alarmPlayer.volume * volMultiplier
                alarmPlayer.volume = newVol
                logger.info("Alarm is still playing - volume level boosted to $newVol")
            }
        }, whenMs)
    }

    private fun onRingtoneAnswer(selected: Ringtone) {
        if (selected == currentRingtone) {
            correctAnswer()
        } else {
            wrongAnswer()
        }
    }

    private fun correctAnswer() {
        alarmPlayer.stopAlarm()
        activateAlarmTime = null
        uiInfoService.showToast("Congratulations! You have woken up.")
        if (isThisAlarmLast) {
            logger.debug("Last alarm stopped")
            showLastAlarmDialog()
        } else {
            activity.finish()
        }
    }

    fun showLastAlarmDialog() {
        val title = "ATTENTION!"
        val message = "This was the last alarm. WAKE UP!!!"
        val alertBuilder: AlertDialog.Builder = AlertDialog.Builder(activity)
        alertBuilder.setMessage(message)
        alertBuilder.setTitle(title)
        alertBuilder.setPositiveButton("OK, I have woken up") { dialog, which -> }
        alertBuilder.setCancelable(true)
        val alert: AlertDialog = alertBuilder.create()
        alert.setOnShowListener { arg0 ->
            alert.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(-0x1)
        }
        alert.show()
        vibratorService.vibrate(1000)
    }

    private fun wrongAnswer() {
        uiInfoService.showToast("Wrong answer, you morron!")
        vibratorService.vibrate(1000)
        val ringtones: List<Ringtone?> = ringtoneManager.allRingtones.shuffled()
        ringtoneListAdapter?.clear()
        ringtoneListAdapter?.addAll(ringtones)
        ringtoneListAdapter?.notifyDataSetChanged()
    }


    // alarms from near future (now < alarm time < next hour)
    private val isThisAlarmLast: Boolean
        get() {
            val alarmsConfig: AlarmsConfig = alarmsPersistenceService.readAlarmsConfig()
            val alarmTriggers = alarmsConfig.alarmTriggers
            // alarms from near future (now < alarm time < next hour)
            val nearAlarms = alarmTriggers
                    .map { it.triggerTime }
                    .filter { it.isAfterNow }
                    .filter { it.isBefore(DateTime.now().plusHours(1)) }
                    .count()
            return nearAlarms == 0
        }

}
