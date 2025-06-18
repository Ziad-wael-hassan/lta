package com.elfinsaddle.data.remote

/**
 * A sealed class to represent the result of a network operation.
 * It provides a clean way to handle success, API errors (like 404, 500),
 * and network failures (like no internet connection).
 */
sealed class NetworkResult<out T> {
    /**
     * Represents a successful network request.
     * @param data The data returned from the API.
     */
    data class Success<out T>(val data: T) : NetworkResult<T>()

    /**
     * Represents a specific error returned by the server API.
     * @param code The HTTP status code.
     * @param message A descriptive error message from the server, if available.
     */
    data class ApiError(val code: Int, val message: String?) : NetworkResult<Nothing>()

    /**
     * Represents a failure in network communication (e.g., no internet, DNS issue).
     */
    object NetworkError : NetworkResult<Nothing>()
}
