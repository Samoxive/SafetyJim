package org.samoxive.safetyjim.discord.commands

import io.vertx.kotlin.ext.web.client.sendAwait
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.json.JSONObject
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.failMessage
import org.samoxive.safetyjim.discord.trySendMessage
import org.samoxive.safetyjim.httpClient
import java.awt.Color
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val iconEmojis = mapOf(
    "clear-day" to ":sunny:",
    "clear-night" to ":crescent_moon:",
    "rain" to ":cloud_rain:",
    "snow" to ":cloud_snow:",
    "sleet" to ":cloud_snow:",
    "partly-cloudy-day" to ":partly_sunny:",
    "partly-cloudy-night" to ":partly_sunny:",
    "fog" to ":fog:",
    "cloudy" to ":cloud:",
    "wind" to ":wind_blowing_face:"
)
private val dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT' ").withZone(ZoneOffset.UTC)

fun fahrenheitToCelcius(degree: Float): Float = ((degree - 32) * 5) / 9

class Weather : Command() {
    override val usages: Array<String> = arrayOf()
    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val message = event.message
        val channel = event.channel

        if (args.isEmpty()) {
            return true
        }

        val geocodeResponse = httpClient.get(443, "maps.googleapis.com", "/maps/api/geocode/json")
            .addQueryParam("key", bot.config.jim.geocode_token)
            .addQueryParam("address", args)
            .sendAwait()

        val geocodeObject = JSONObject(geocodeResponse.bodyAsString())
        if (geocodeObject.getString("status") != "OK") {
            message.failMessage("Could not detect given address!")
            return false
        }

        val locationObject = geocodeObject.getJSONArray("results").getJSONObject(0)
        val address = locationObject.getString("formatted_address")
        val geometryObject = locationObject.getJSONObject("geometry")
        val lat = geometryObject.getJSONObject("location").getFloat("lat")
        val lng = geometryObject.getJSONObject("location").getFloat("lng")

        val darkskyResponse = httpClient.get(443, "api.darksky.net", "/forecast/${bot.config.jim.darksky_token}/$lat,$lng")
            .sendAwait()

        val weatherObject = JSONObject(darkskyResponse.bodyAsString())
        val localTimeOffset = weatherObject.getInt("offset")
        val localDate = Instant.ofEpochMilli(System.currentTimeMillis() + localTimeOffset * 60 * 60 * 1000)

        val localDateString = if (localTimeOffset > 0) {
            dateFormatter.format(localDate) + "+$localTimeOffset"
        } else {
            dateFormatter.format(localDate) + "$localTimeOffset"
        }

        val currentWeatherObject = weatherObject.getJSONObject("currently")
        val summary = currentWeatherObject.getString("summary")
        val tempF = currentWeatherObject.getFloat("temperature").toInt() // get rid of decimal
        val tempC = fahrenheitToCelcius(tempF.toFloat()).toInt()
        val humidity = (currentWeatherObject.getFloat("humidity") * 100).toInt()
        val icon = currentWeatherObject.getString("icon")

        val embedBuilder = EmbedBuilder()
            .setColor(Color(0x4286F4))
            .setTitle("Weather in $address")
            .setFooter("Local Time: $localDateString", null)
            .addField("Summary", summary, false)
            .addField("Temperature", "$tempC °C / $tempF °F", true)
            .addField("Humidity", "$humidity%", true)
            .setDescription(iconEmojis[icon])
        channel.trySendMessage(embedBuilder.build(), message)
        return false
    }
}
