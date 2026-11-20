package io.github.smicca.lector

object TestPassage {
    const val RAW_TEXT: String = """From the ancient typologies of the Old Testament to the definitive revelation in Jesus Christ, salvation history demonstrates divine continuity. In the days of Abraham, the enigmatic priest-king Melchizedek offered bread and wine, prefiguring the eternal priesthood fulfilled at the Last Supper. Centuries later, during the Babylonian exile prophesied by Habakkuk, King Nebuchadnezzar destroyed the Temple in Jerusalem, scattering the covenant people. Yet God remained faithful to His promises: under Zerubbabel, the faithful remnant returned from exile to rebuild the sacred sanctuary, looking forward to an everlasting covenant. The Greek translation of the Hebrew Scriptures, known as the Septuagint, prepared the linguistic soil for the apostolic proclamation throughout the Greco-Roman world.
When the fullness of time arrived, God sent His Son, born of the Blessed Virgin Mary, whom the Council of Ephesus solemnly declared to be Theotokos, the Mother of God. The early Church defended this Christological truth against various heresies, culminating in the Definition of Chalcedon. There the Church affirmed that Jesus Christ possesses two complete natures, divine and human, united without confusion, change, division, or separation in the hypostatic union. As the Council of Nicaea taught, the Son is consubstantial with the Father, possessing identical divine essence. Later Western tradition, seeking to safeguard this co-equality of the divine persons, confessed that the Holy Spirit proceeds from the Father and the Son, an affirmation known as the Filioque.
Patristic witness establishes that the apostolic deposit of faith was guarded and passed down intact. Saint Ignatius of Antioch, on his way to martyrdom in Rome, emphasized the necessity of communion with the bishop and recognized the Eucharist as the medicine of immortality. Saint Irenaeus of Lyons defended apostolic succession against Gnostic counterfeits, insisting that truth is preserved in the public tradition of the apostolic sees. Tertullian coined theological vocabulary that clarified how three divine persons subsist in one undivided substance. In Antioch and Constantinople, Saint John Chrysostom preached with golden eloquence on the grandeur of the divine liturgy and the reverence due to the holy mysteries.
The Catholic Church professes that the sacraments are not mere symbolic gestures, but efficacious signs instituted by Christ to confer grace ex opere operato. In the celebration of the Eucharist, the memorial sacrifice is not an empty recollection of the past, but a real anamnesis that makes present the once-for-all sacrifice of Calvary. The Lord Himself declared in Jn 6:53, 'Truly, truly, I say to you, unless you eat the flesh of the Son of Man and drink his blood, you have no life in you.' The core apostolic kerygma was summarized by Saint Paul in 1 Cor 15:3-8, proclaiming that Christ died for our sins in accordance with the Scriptures, was buried, and rose on the third day, appearing to Cephas and the Twelve.
To guide believers through the complexities of doctrine, the Living Magisterium exercises its teaching authority under the guidance of the Holy Spirit. Grace remains foundational to every stage of justification and sanctification. As defined in CCC 1996, 'Our justification comes from the grace of God. Grace is favor, the free and undeserved help that God gives us to respond to his call to become children of God, adoptive sons, partakers of the divine nature and of eternal life.' Thus, from the ancient priesthood of Melchizedek to the contemporary witness of the Church, the mystery of redemption remains unified, luminous, and preserved for all generations."""

    val HARD_WORDS = listOf(
        "Melchizedek",
        "Nebuchadnezzar",
        "Habakkuk",
        "Zerubbabel",
        "Theotokos",
        "hypostatic union",
        "consubstantial",
        "Filioque",
        "anamnesis",
        "ex opere operato",
        "Tertullian",
        "Irenaeus",
        "Ignatius of Antioch",
        "Chrysostom",
        "Septuagint",
        "Magisterium",
        "1 Cor 15:3-8",
        "CCC 1996",
        "Jn 6:53"
    )

    fun getNormalizedSentences(): List<String> {
        val normalized = SpeechNormalizer.normalize(RAW_TEXT)
        return SpeechNormalizer.splitSentences(normalized)
    }
}
