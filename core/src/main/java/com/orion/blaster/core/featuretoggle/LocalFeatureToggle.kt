package com.orion.blaster.core.featuretoggle

data class LocalFeatureToggle(
    val enabled: Boolean = true,
    val includeCandidateAssociated: Boolean = false,
)
