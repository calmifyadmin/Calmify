package com.lifo.auth.domain

import com.google.firebase.auth.FirebaseAuth
import com.lifo.util.usecase.NoParamUseCase
/**
 * Signs out the current user from Firebase Auth.
 */
class SignOutUseCase(
    private val auth: FirebaseAuth
) : NoParamUseCase<Unit> {

    override suspend fun invoke() {
        auth.signOut()
    }
}
