package com.blockchain.utils

infix fun Int.isLastIn(list: List<*>): Boolean = this == list.size - 1

infix fun Int.isNotLastIn(list: List<*>): Boolean = isLastIn(list).not()
