package com.awareness.core.domain.usecase

import com.awareness.core.domain.model.DailyAggregate
import kotlinx.coroutines.flow.Flow

interface GetDailyStatsUseCase {
    operator fun invoke(days: Int = 7): Flow<List<DailyAggregate>>
}
