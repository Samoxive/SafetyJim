package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.ext.web.client.sendAwait
import org.json.JSONObject
import org.samoxive.safetyjim.database.SettingsTable
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.await
import org.samoxive.safetyjim.httpClient
import org.samoxive.safetyjim.server.AbstractEndpoint
import org.samoxive.safetyjim.server.Result
import org.samoxive.safetyjim.server.Status
import org.samoxive.safetyjim.tryhardAsync

const val captcha_template =
    """
<html>

<head>
    <link rel="shortcut icon" href="https://safetyjim.xyz/favicon.ico">
    <title>Safety Jim</title>
    <script src='https://www.google.com/recaptcha/api.js' async defer></script>
</head>

<body style="background-color:#222">
    <div style="display:flex;justify-content:center;align-items:center;height:100%">
        <div>
            <form action="/captcha/#guildId/#userId" method="POST" id="captcha-form">
                <div class="g-recaptcha" data-sitekey="6LdH5IsUAAAAAPt5tQVPWUFKPGynkFh2lq5jNVun" data-callback="onSuccess" data-theme="dark"></div>
            </form>
        </div>
    </div>
    <script>
        function onSuccess(res) {
            document.getElementById("captcha-form").submit();
        }
    </script>
</body>

</html>
"""

class CaptchaPageEndpoint(bot: DiscordBot) : AbstractEndpoint(bot) {
    override val route = "/captcha/:guildId/:userId"
    override val method = HttpMethod.GET

    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse): Result {
        val guildId = request.getParam("guildId") ?: return Result(Status.SERVER_ERROR)
        val guild = bot.getGuild(guildId) ?: return Result(Status.NOT_FOUND)
        val userId = request.getParam("userId") ?: return Result(Status.SERVER_ERROR)
        val member = guild.getMemberById(userId) ?: return Result(Status.NOT_FOUND)

        response.putHeader("Content-Type", "text/html")
        response.end(captcha_template.replace("#guildId", guild.id).replace("#userId", member.user.id))
        return Result(Status.OK)
    }
}

class CaptchaSubmitEndpoint(bot: DiscordBot) : AbstractEndpoint(bot) {
    override val route = "/captcha/:guildId/:userId"
    override val method = HttpMethod.POST

    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse): Result {
        val guildId = request.getParam("guildId") ?: return Result(Status.SERVER_ERROR)
        val guild = bot.getGuild(guildId) ?: return Result(Status.NOT_FOUND)
        val userId = request.getParam("userId") ?: return Result(Status.SERVER_ERROR)
        val member = guild.getMemberById(userId) ?: return Result(Status.NOT_FOUND)

        val captchaBody = request.formAttributes().get("g-recaptcha-response") ?: return Result(Status.BAD_REQUEST)
        val captchaResponse = httpClient.post(443, "google.com", "/recaptcha/api/siteverify")
            .putHeader("Content-Length", "0")
            .addQueryParam("secret", bot.config.server.recaptcha_secret)
            .addQueryParam("response", captchaBody)
            .sendAwait()

        val responseObject = JSONObject(captchaResponse.bodyAsString())
        if (responseObject.has("success")) {
            val success = responseObject.getBoolean("success")
            if (!success) {
                return Result(Status.FORBIDDEN)
            }
        } else {
            return Result(Status.SERVER_ERROR, "Google be trippin.")
        }

        val settings = SettingsTable.getGuildSettings(guild, bot.config)
        val holdingRoomRoleId = settings.holdingRoomRoleId
            ?: return Result(Status.BAD_REQUEST, "Holding room role isn't set up!")
        val holdingRoomRole = guild.getRoleById(holdingRoomRoleId)
            ?: return Result(Status.BAD_REQUEST, "Holding room role isn't set up correctly!")
        tryhardAsync { guild.addRoleToMember(member, holdingRoomRole).await() }
        response.end("You have been approved to join! You can close this window.")
        return Result(Status.OK)
    }
}
