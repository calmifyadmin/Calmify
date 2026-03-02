package com.lifo.util.usecase

/**
 * Base UseCase interfaces for the domain layer.
 *
 * UseCases encapsulate single business operations, promoting
 * testability and reuse across ViewModels.
 *
 * Naming convention: VerbNounUseCase (e.g., GetInsightsUseCase, SaveDiaryUseCase)
 */

/**
 * Suspending use case for one-shot operations (e.g., save, delete, submit).
 */
interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): R
}

/**
 * Use case that returns a Flow for reactive/streaming operations (e.g., observe, listen).
 */
interface FlowUseCase<in P, out R> {
    operator fun invoke(params: P): kotlinx.coroutines.flow.Flow<R>
}

/**
 * Suspending use case with no parameters.
 */
interface NoParamUseCase<out R> {
    suspend operator fun invoke(): R
}

/**
 * Flow use case with no parameters.
 */
interface NoParamFlowUseCase<out R> {
    operator fun invoke(): kotlinx.coroutines.flow.Flow<R>
}
