package org.nontrivialpursuit.ingeblikt

data class BundleUtility(val injection: Boolean, val distribution: Boolean)

fun Subscriptions.classifyBundle(bundle: BundleDescriptor): BundleUtility {
    return this.types.get(bundle.type)?.groups?.get(bundle.origin)?.names?.get(bundle.name)?.let { deets ->
        val injection_rangematch = (bundle.version in (deets.injected_version?.let {
            Math.max(
                it, deets.injection_version_min
            )
        } ?: deets.injection_version_min)..deets.injection_version_max)
        val injectalicious = injection_rangematch && (deets.injected_version?.let { bundle.version > it } ?: true)
        val distributialicious = (bundle.version in deets.p2p_version_min..deets.p2p_version_max) || injection_rangematch
        return@let BundleUtility(injectalicious, distributialicious)
    } ?: BundleUtility(injection = false, distribution = false)
}

fun classifyBundles(subs: Subscriptions, bundles: List<BundleDescriptor>): Map<BundleDescriptor, BundleUtility> {
    return bundles.associateWith { subs.classifyBundle(it) }
}

fun acquirables(subs: Subscriptions, local_bundles: List<BundleIndexItem>, remote_bundles: List<BundleIndexItem>): Set<BundleDescriptor> {
    return classifyBundles(subs, remote_bundles.map { it.bundle }).filter {
        it.value.distribution || it.value.injection
    }.keys - local_bundles.map { it.bundle }.toSet()
}

fun injectables(subs: Subscriptions, local_bundles: List<BundleIndexItem>): Set<BundleDescriptor> {
    return classifyBundles(subs, local_bundles.map { it.bundle }).filter {
        it.value.injection
    }.keys
}

fun deletables(subs: Subscriptions, local_bundles: List<BundleIndexItem>): Set<BundleDescriptor> {
    return classifyBundles(subs, local_bundles.map { it.bundle }).filter {
        !(it.value.injection || it.value.distribution)
    }.keys
}
