package com.beatohm.data

object PresetThemes {

    fun getAll(): List<UserTheme> = listOf(
        neonNight(),
        oceanDeep(),
        forestGreen(),
        sunsetOrange(),
        royalPurple(),
        minimalWhite()
    )

    fun neonNight() = UserTheme(
        id = 1,
        name = "Neon Night",
        primaryColor = 0xFF9D35FF.toInt(),
        secondaryColor = 0xFFFF304F.toInt(),
        accentColor = 0xFF00E5FF.toInt(),
        backgroundColor = 0xFF0B0910.toInt(),
        surfaceColor = 0xFF12101A.toInt(),
        textColor = 0xFFFFFFFF.toInt(),
        iconPackId = "default",
        playerLayoutId = "classic",
        isPreset = true
    )

    fun oceanDeep() = UserTheme(
        id = 2,
        name = "Ocean Deep",
        primaryColor = 0xFF3D7BFF.toInt(),
        secondaryColor = 0xFF00C2A8.toInt(),
        accentColor = 0xFF64B5F6.toInt(),
        backgroundColor = 0xFF0A1628.toInt(),
        surfaceColor = 0xFF111E30.toInt(),
        textColor = 0xFFE8F0FE.toInt(),
        iconPackId = "default",
        playerLayoutId = "classic",
        isPreset = true
    )

    fun forestGreen() = UserTheme(
        id = 3,
        name = "Forest Green",
        primaryColor = 0xFF12A150.toInt(),
        secondaryColor = 0xFF4CAF50.toInt(),
        accentColor = 0xFF81C784.toInt(),
        backgroundColor = 0xFF0A1410.toInt(),
        surfaceColor = 0xFF122218.toInt(),
        textColor = 0xFFE8F5E9.toInt(),
        iconPackId = "default",
        playerLayoutId = "classic",
        isPreset = true
    )

    fun sunsetOrange() = UserTheme(
        id = 4,
        name = "Sunset Orange",
        primaryColor = 0xFFFF6B2C.toInt(),
        secondaryColor = 0xFFE8A600.toInt(),
        accentColor = 0xFFFFAB40.toInt(),
        backgroundColor = 0xFF1A0E08.toInt(),
        surfaceColor = 0xFF221610.toInt(),
        textColor = 0xFFFFF3E0.toInt(),
        iconPackId = "default",
        playerLayoutId = "classic",
        isPreset = true
    )

    fun royalPurple() = UserTheme(
        id = 5,
        name = "Royal Purple",
        primaryColor = 0xFF8B5CF6.toInt(),
        secondaryColor = 0xFFA78BFA.toInt(),
        accentColor = 0xFFC4B5FD.toInt(),
        backgroundColor = 0xFF0E0A1A.toInt(),
        surfaceColor = 0xFF18122A.toInt(),
        textColor = 0xFFF3E8FF.toInt(),
        iconPackId = "default",
        playerLayoutId = "classic",
        isPreset = true
    )

    fun minimalWhite() = UserTheme(
        id = 6,
        name = "Minimal White",
        primaryColor = 0xFF1A1A22.toInt(),
        secondaryColor = 0xFF3D3D4A.toInt(),
        accentColor = 0xFF9D35FF.toInt(),
        backgroundColor = 0xFFFAF9FB.toInt(),
        surfaceColor = 0xFFFFFFFF.toInt(),
        textColor = 0xFF1A1A22.toInt(),
        iconPackId = "outline",
        playerLayoutId = "compact",
        isPreset = true
    )

    fun getDefault(): UserTheme = neonNight()
}