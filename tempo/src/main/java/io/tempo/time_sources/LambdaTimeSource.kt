package io.tempo.time_sources

import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.tempo.TimeSource
import io.tempo.TimeSourceConfig

class LambdaTimeSource(
    private val id: String = "default-lambda-call",
    private val priority: Int = 10,
    private val initLambda: (result: (time: Long) -> Unit, error: (e: Throwable) -> Unit) -> Unit,
    private val funCallbackSuccess: ((time: Long) -> Unit)? = null,
    private val funCallbackError: ((throwable: Throwable) -> Unit)? = null
) : TimeSource {

    override fun config() = TimeSourceConfig(id = id, priority = priority)

    override fun requestTime(): Single<Long> =
        Single.create<Long> { emitter ->
            try {
                initLambda({
                    emitter.onSuccess(it)
                }, {
                    emitter.tryOnError(it)
                })
            } catch (t: Throwable) {
                emitter.tryOnError(t)
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .doOnSuccess {
                funCallbackSuccess?.invoke(it)
            }.doOnError {
                funCallbackError?.invoke(it)
            }
}