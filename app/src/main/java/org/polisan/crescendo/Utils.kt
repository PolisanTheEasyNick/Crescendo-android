package org.polisan.crescendo

import android.util.Log

object Utils {
    fun parseData(input: String): Map<String, String> {
        val dataMap: MutableMap<String, String> = HashMap()

        val pairs = input.split("||")
            .toTypedArray()

        var i = 0
        while (i < pairs.size - 1) {
            val key = pairs[i]
            val value = pairs[i + 1]
            dataMap[key] = value
            i += 2
        }
        return dataMap
    }

    fun parseList(input: String): Pair<Int, List<Pair<String, String>>> {
        val elements = mutableListOf<Pair<String, String>>()
        Log.d("PARSE", "Starting parsing player info. input: $input")
        val items = input.split("||")
        var index = -1
        Log.d("PARSE", "Items: $items")
        if (items.size >= 3) {
            index = items[0].toInt()
            for (i in 1 until items.size - 1 step 2) {
                val element = items[i]
                val id = items[i + 1]
                elements.add(element to id)
            }
        }
        Log.d("PARSE", "Index fetched: $index")
        Log.d("PARSE", "Elements fetched: $elements")
        return index to elements
    }


    fun convertTimeStringToSeconds(timeString: String): Int {
        val parts = timeString.split(":")
        val seconds = parts.last().toInt()

        val minutes = if (parts.size > 1) {
            parts[parts.size - 2].toInt()
        } else {
            0
        }

        val hours = if (parts.size > 2) {
            parts[parts.size - 3].toInt()
        } else {
            0
        }

        return hours * 3600 + minutes * 60 + seconds
    }

    fun calculatePositionPercentage(positionSeconds: Int, lengthSeconds: Int): Float {
        val positionFloat = positionSeconds.toFloat()
        val lengthFloat = lengthSeconds.toFloat()
        return if (lengthFloat > 0f) {
            positionFloat / lengthFloat
        } else {
            0f
        }
    }
}