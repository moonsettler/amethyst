package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip19
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import java.math.BigDecimal
import java.util.*


@Immutable
open class Event(
    val id: HexKey,
    @JsonProperty("pubkey")
    val pubKey: HexKey,
    @JsonProperty("created_at")
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: HexKey
) : EventInterface {

    override fun countMemory(): Long {
        return 12L +
            id.bytesUsedInMemory() +
            pubKey.bytesUsedInMemory() +
            tags.sumOf { it.sumOf { it.bytesUsedInMemory() } } +
            content.bytesUsedInMemory() +
            sig.bytesUsedInMemory()
    }

    override fun id(): HexKey = id

    override fun pubKey(): HexKey = pubKey

    override fun createdAt(): Long = createdAt

    override fun kind(): Int = kind

    override fun tags(): List<List<String>> = tags

    override fun content(): String = content

    override fun sig(): HexKey = sig

    override fun toJson(): String = mapper.writeValueAsString(toJsonObject())

    fun hasAnyTaggedUser() = tags.any { it.size > 1 && it[0] == "p" }

    override fun taggedUsers() = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }
    override fun taggedEvents() = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }

    override fun taggedUrls() = tags.filter { it.size > 1 && it[0] == "r" }.map { it[1] }

    override fun taggedEmojis() = tags.filter { it.size > 2 && it[0] == "emoji" }.map { EmojiUrl(it[1], it[2]) }

    override fun isSensitive() = tags.any {
        (it.size > 0 && it[0].equals("content-warning", true)) ||
            (it.size > 1 && it[0] == "t" && it[1].equals("nsfw", true)) ||
            (it.size > 1 && it[0] == "t" && it[1].equals("nude", true))
    }

    override fun subject() = tags.firstOrNull() { it.size > 1 && it[0] == "subject" }?.get(1)

    override fun zapraiserAmount() = tags.firstOrNull() {
        (it.size > 1 && it[0] == "zapraiser")
    }?.get(1)?.toLongOrNull()

    override fun zapAddress() = tags.firstOrNull { it.size > 1 && it[0] == "zap" }?.get(1)

    override fun taggedAddresses() = tags.filter { it.size > 1 && it[0] == "a" }.mapNotNull {
        val aTagValue = it[1]
        val relay = it.getOrNull(2)

        ATag.parse(aTagValue, relay)
    }

    override fun hashtags() = tags.filter { it.size > 1 && it[0] == "t" }.map { it[1] }
    override fun geohashes() = tags.filter { it.size > 1 && it[0] == "g" }.map { it[1] }

    override fun matchTag1With(text: String) = tags.any { it.size > 1 && it[1].contains(text, true) }

    override fun isTaggedUser(idHex: String) = tags.any { it.size > 1 && it[0] == "p" && it[1] == idHex }

    override fun isTaggedEvent(idHex: String) = tags.any { it.size > 1 && it[0] == "e" && it[1] == idHex }

    override fun isTaggedAddressableNote(idHex: String) = tags.any { it.size > 1 && it[0] == "a" && it[1] == idHex }

    override fun isTaggedAddressableNotes(idHexes: Set<String>) = tags.any { it.size > 1 && it[0] == "a" && it[1] in idHexes }

    override fun isTaggedHash(hashtag: String) = tags.any { it.size > 1 && it[0] == "t" && it[1].equals(hashtag, true) }

    override fun isTaggedGeoHash(hashtag: String) = tags.any { it.size > 1 && it[0] == "g" && it[1].startsWith(hashtag, true) }
    override fun isTaggedHashes(hashtags: Set<String>) = tags.any { it.size > 1 && it[0] == "t" && it[1].lowercase() in hashtags }
    override fun isTaggedGeoHashes(hashtags: Set<String>) = tags.any { it.size > 1 && it[0] == "g" && it[1].lowercase() in hashtags }
    override fun firstIsTaggedHashes(hashtags: Set<String>) = tags.firstOrNull { it.size > 1 && it[0] == "t" && it[1].lowercase() in hashtags }?.getOrNull(1)

    override fun firstIsTaggedAddressableNote(addressableNotes: Set<String>) = tags.firstOrNull { it.size > 1 && it[0] == "a" && it[1] in addressableNotes }?.getOrNull(1)

    override fun isTaggedAddressableKind(kind: Int): Boolean {
        val kindStr = kind.toString()
        return tags.any { it.size > 1 && it[0] == "a" && it[1].startsWith(kindStr) }
    }

    override fun getTagOfAddressableKind(kind: Int): ATag? {
        val kindStr = kind.toString()
        val aTag = tags
            .firstOrNull { it.size > 1 && it[0] == "a" && it[1].startsWith(kindStr) }
            ?.getOrNull(1)
            ?: return null

        return ATag.parse(aTag, null)
    }

    override fun getPoWRank(): Int {
        var rank = 0
        for (i in 0..id.length) {
            if (id[i] == '0') {
                rank += 4
            } else if (id[i] in '4'..'7') {
                rank += 1
                break
            } else if (id[i] in '2'..'3') {
                rank += 2
                break
            } else if (id[i] == '1') {
                rank += 3
                break
            } else {
                break
            }
        }
        return rank
    }

    override fun getGeoHash(): String? {
        return tags.firstOrNull { it.size > 1 && it[0] == "g" }?.get(1)?.ifBlank { null }
    }

    override fun getReward(): BigDecimal? {
        return try {
            tags.firstOrNull { it.size > 1 && it[0] == "reward" }?.get(1)?.let { BigDecimal(it) }
        } catch (e: Exception) {
            null
        }
    }

    open fun toNIP19(): String {
        return if (this is AddressableEvent) {
            ATag(kind, pubKey, dTag(), null).toNAddr()
        } else {
            Nip19.createNEvent(id, pubKey, kind, null)
        }
    }

    fun toNostrUri(): String {
        return "nostr:${toNIP19()}"
    }

    fun hasCorrectIDHash() = id.equals(generateId())
    fun hasVerifedSignature() = CryptoUtils.verifySignature(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))

    /**
     * Checks if the ID is correct and then if the pubKey's secret key signed the event.
     */
    override fun checkSignature() {
        if (!hasCorrectIDHash()) {
            throw Exception(
                """|Unexpected ID.
                   |  Event: ${toJson()}
                   |  Actual ID: $id
                   |  Generated: ${generateId()}
                """.trimIndent()
            )
        }
        if (!hasVerifedSignature()) {
            throw Exception("""Bad signature!""")
        }
    }

    override fun hasValidSignature(): Boolean {
        return try {
            hasCorrectIDHash() && hasVerifedSignature()
        } catch (e: Exception) {
            Log.e("Event", "Fail checking if event $id has a valid signature", e)
            false
        }
    }

    fun makeJsonForId(): String {
        return makeJsonForId(pubKey, createdAt, kind, tags, content)
    }

    private fun generateId(): String {
        return CryptoUtils.sha256(makeJsonForId().toByteArray()).toHexKey()
    }

    private class EventDeserializer : StdDeserializer<Event>(Event::class.java) {
        override fun deserialize(jp: JsonParser, ctxt: DeserializationContext): Event {
            return fromJson(jp.codec.readTree(jp))
        }
    }

    private class GossipDeserializer : StdDeserializer<Gossip>(Gossip::class.java) {
        override fun deserialize(jp: JsonParser, ctxt: DeserializationContext): Gossip {
            val jsonObject: JsonNode = jp.codec.readTree(jp)
            return Gossip(
                id = jsonObject.get("id")?.asText()?.intern(),
                pubKey = jsonObject.get("pubkey")?.asText()?.intern(),
                createdAt = jsonObject.get("created_at")?.asLong(),
                kind = jsonObject.get("kind")?.asInt(),
                tags = jsonObject.get("tags")?.map {
                    it.mapNotNull { s -> if (s?.isNull ?: true) null else s.asText().intern() }
                },
                content = jsonObject.get("content")?.asText()
            )
        }
    }

    private class EventSerializer: StdSerializer<Event>(Event::class.java) {
        override fun serialize(event: Event, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeStartObject()
            gen.writeStringField("id", event.id)
            gen.writeStringField("pubkey", event.pubKey)
            gen.writeNumberField("created_at", event.createdAt)
            gen.writeNumberField("kind", event.kind)
            gen.writeArrayFieldStart("tags")
            event.tags.forEach { tag ->
                gen.writeArray(tag.toTypedArray(), 0, tag.size)
            }
            gen.writeEndArray()
            gen.writeStringField("content", event.content)
            gen.writeStringField("sig", event.sig)
            gen.writeEndObject()
        }
    }

    private class GossipSerializer: StdSerializer<Gossip>(Gossip::class.java) {
        override fun serialize(event: Gossip, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeStartObject()
            event.id?.let { gen.writeStringField("id", it) }
            event.pubKey?.let { gen.writeStringField("pubkey", it) }
            event.createdAt?.let { gen.writeNumberField("created_at", it) }
            event.kind?.let { gen.writeNumberField("kind", it) }
            event.tags?.let {
                gen.writeArrayFieldStart("tags")
                event.tags.forEach { tag ->
                    gen.writeArray(tag.toTypedArray(), 0, tag.size)
                }
                gen.writeEndArray()
            }
            event.content?.let { gen.writeStringField("content", it)  }
            gen.writeEndObject()
        }
    }

    fun toJsonObject(): JsonNode {
        val factory = mapper.nodeFactory

        return factory.objectNode().apply {
            put("id", id)
            put("pubkey", pubKey)
            put("created_at", createdAt)
            put("kind", kind)
            put(
                "tags",
                factory.arrayNode(tags.size).apply {
                    tags.forEach { tag ->
                        add(
                            factory.arrayNode(tag.size).apply {
                                tag.forEach { add(it) }
                            }
                        )
                    }
                }
            )
            put("content", content)
            put("sig", sig)
        }
    }

    companion object {
        val mapper = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(SimpleModule()
                .addSerializer(Event::class.java, EventSerializer())
                .addDeserializer(Event::class.java, EventDeserializer())
                .addSerializer(Gossip::class.java, GossipSerializer())
                .addDeserializer(Gossip::class.java, GossipDeserializer())
                .addDeserializer(Response::class.java, ResponseDeserializer())
                .addDeserializer(Request::class.java, RequestDeserializer())
            )

        fun fromJson(jsonObject: JsonNode): Event {
            return EventFactory.create(
                id = jsonObject.get("id").asText().intern(),
                pubKey = jsonObject.get("pubkey").asText().intern(),
                createdAt = jsonObject.get("created_at").asLong(),
                kind = jsonObject.get("kind").asInt(),
                tags = jsonObject.get("tags").map {
                    it.mapNotNull { s -> if (s.isNull) null else s.asText().intern() }
                },
                content = jsonObject.get("content").asText(),
                sig = jsonObject.get("sig").asText()
            )
        }

        fun fromJson(json: String): Event = mapper.readValue(json, Event::class.java)
        fun toJson(event: Event): String = mapper.writeValueAsString(event)

        fun makeJsonForId(pubKey: HexKey, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String {
            val factory = mapper.nodeFactory
            val rawEvent = factory.arrayNode(6).apply {
                add(0)
                add(pubKey)
                add(createdAt)
                add(kind)
                add(
                    factory.arrayNode(tags.size).apply {
                        tags.forEach { tag ->
                            add(
                                factory.arrayNode(tag.size).apply {
                                    tag.forEach { add(it) }
                                }
                            )
                        }
                    }
                )
                add(content)
            }

            return mapper.writeValueAsString(rawEvent)
        }

        fun generateId(pubKey: HexKey, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): ByteArray {
            return CryptoUtils.sha256(makeJsonForId(pubKey, createdAt, kind, tags, content).toByteArray())
        }

        fun create(privateKey: ByteArray, kind: Int, tags: List<List<String>> = emptyList(), content: String = "", createdAt: Long = TimeUtils.now()): Event {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = Companion.generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey).toHexKey()
            return Event(id.toHexKey(), pubKey, createdAt, kind, tags, content, sig)
        }
    }
}

@Immutable
open class WrappedEvent(
    id: HexKey,
    @JsonProperty("pubkey")
    pubKey: HexKey,
    @JsonProperty("created_at")
    createdAt: Long,
    kind: Int,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient
    var host: Event? = null // host event to broadcast when needed
}

@Immutable
interface AddressableEvent {
    fun dTag(): String
    fun address(): ATag
}

fun String.bytesUsedInMemory(): Int {
    return (8 * ((((this.length) * 2) + 45) / 8))
}
