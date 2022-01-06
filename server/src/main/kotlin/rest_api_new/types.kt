package com.rest_api.types

import java.math.BigInteger
import java.sql.Timestamp
import java.util.*

data class Tr (
    val address: BigInteger,
    val coins: ULong
)

data class UTxO(
    val txId: BigInteger,
    val address: BigInteger
)

data class Tx(
    val txId: BigInteger,
    val inputs: List<UTxO>,
    val outputs: List<Tr>,
    val timestamp: Timestamp,
)

data class RequestBodyType(
    val address: BigInteger,
    val payload: Optional<Any>
)