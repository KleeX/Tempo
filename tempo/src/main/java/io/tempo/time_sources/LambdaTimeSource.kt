/*
 * Copyright 2017 Allan Yoshio Hasegawa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.tempo.time_sources

import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.tempo.TimeSource
import io.tempo.TimeSourceConfig

/**
 * A [TimeSource] implementation using a more forgiving SNTP algorithm. It queries the [ntpPool]
 * five times concurrently, then, removes all failures and queries where the round trip took more
 * than [maxRoundTripMs]. If one or more queries succeeds, we take the one with the median round
 * trip time and return it.
 *
 * @param[id] The unique time source id.
 * @param[priority] The time source priority.
 * @param[ntpPool] The address of the NTP pool.
 * @param[maxRoundTripMs] The maximum allowed round trip time in milliseconds.
 * @param[timeoutMs] The maximum time allowed per each query, in milliseconds.
 */
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