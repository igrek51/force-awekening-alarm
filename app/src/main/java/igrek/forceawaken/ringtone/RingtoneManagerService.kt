package igrek.forceawaken.ringtone

import igrek.forceawaken.info.logger.Logger
import igrek.forceawaken.info.logger.LoggerFactory
import igrek.forceawaken.inject.LazyExtractor
import igrek.forceawaken.inject.LazyInject
import igrek.forceawaken.inject.appFactory
import igrek.forceawaken.system.filesystem.ExternalCardService
import java.io.File
import java.util.*

class RingtoneManagerService(
        externalCardService: LazyInject<ExternalCardService> = appFactory.externalCardService,
) {
    private val externalCardService by LazyExtractor(externalCardService)

    private val logger: Logger = LoggerFactory.logger

    private val random = Random()

    val randomRingtone: Ringtone
        get() {
            val ringtones = allRingtones
            return ringtones[random.nextInt(ringtones.size)]

        }

    val allRingtones: List<Ringtone>
        get() {
            val ringtonesPath = "$externalStorageDirectory/Android/data/igrek.forceawaken/ringtones"
            val ringtonesDir = File(ringtonesPath)
            if (!ringtonesDir.exists()) {
                logger.warn("ringtones dir does not exist")
                ringtonesDir.mkdirs()
            }
            val ringtones: MutableList<Ringtone> = ArrayList()
            for (file in ringtonesDir.listFiles()) {
                val name = getRingtoneName(file)
                ringtones.add(Ringtone(file, name))
            }
            return ringtones
        }

    init {
        externalCardService.get().ensureAppDataDirExists()
    }

    private val externalStorageDirectory: String?
        get() = externalCardService.externalSDPath

    private fun getRingtoneName(ringtone: File): String {
        return ringtone.name.replace("\\.mp3$".toRegex(), "")
    }


}