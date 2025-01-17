/*
 * Jopiter APP
 * Copyright (C) 2021 Leonardo Colman Lopes
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.jopiter.restaurants.repository

import app.jopiter.restaurants.model.Period.Lunch
import app.jopiter.restaurants.model.RestaurantItem
import app.jopiter.restaurants.repository.dynamo.DynamoRestaurantItemRepository
import app.jopiter.restaurants.repository.usp.USPRestaurantItemRepository
import io.kotest.core.spec.IsolationMode.InstancePerTest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.extensions.time.ConstantNowTestListener
import io.kotest.matchers.shouldBe
import io.mockk.called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.DayOfWeek.*
import java.time.LocalDate
import java.time.LocalDate.now

class RestaurantItemRepositoryTest : ShouldSpec({

    val dynamoRepository = mockk<DynamoRestaurantItemRepository>(relaxed = true)
    val uspRepository = mockk<USPRestaurantItemRepository>()

    val target = RestaurantItemRepository(dynamoRepository, uspRepository)

    listener(ConstantNowTestListener(now().with(WEDNESDAY)))

    should("Fetch from USP when cache is empty and date is in current week") {
        val today = now()

        every { uspRepository.fetch(1) } returns setOf(dummyRestaurantItem(today))

        target.get(1, setOf(today)) shouldBe setOf(dummyRestaurantItem(today))

        verify(exactly = 1) { uspRepository.fetch(1) }
    }

    should("Fetch from Cache when value was already fetched from USP") {
        val today = now()

        every { uspRepository.fetch(1) } returns setOf(dummyRestaurantItem(today))

        repeat(2) {
            target.get(1, setOf(today)) shouldBe setOf(dummyRestaurantItem(today))
        }

        verify(exactly = 1) { uspRepository.fetch(1) }
    }

    should("Try to fetch from DynamoDB if date is before current week") {
        val beforeCurrentWeek = now().with(MONDAY).minusDays(1)

        every { dynamoRepository.get(1, beforeCurrentWeek) } returns setOf(dummyRestaurantItem(beforeCurrentWeek))

        target.get(1, setOf(beforeCurrentWeek)) shouldBe setOf(dummyRestaurantItem(beforeCurrentWeek))

        verify { uspRepository wasNot called }
        verify(exactly = 1) { dynamoRepository.get(1, beforeCurrentWeek) }

    }

    should("Try to fetch from DynamoDB if USP returns empty") {
        val today = now()

        every { uspRepository.fetch(1) } returns emptySet()
        every { dynamoRepository.get(1, today) } returns setOf(dummyRestaurantItem(today))

        target.get(1, setOf(today)) shouldBe setOf(dummyRestaurantItem(today))

        verify(exactly = 1) { uspRepository.fetch(1) }
        verify(exactly = 1) { dynamoRepository.get(1, today) }

    }

    should("Keep in cache all values returned from USP") {
        val today = now()
        val tomorrow = today.plusDays(1)

        every { uspRepository.fetch(1) } returns setOf(dummyRestaurantItem(today), dummyRestaurantItem(tomorrow))

        target.get(1, setOf(today)) shouldBe setOf(dummyRestaurantItem(today))
        target.get(1, setOf(today, tomorrow)) shouldBe setOf(dummyRestaurantItem(today), dummyRestaurantItem(tomorrow))

        verify(exactly = 1) { uspRepository.fetch(1) }
        verify(exactly = 0) { dynamoRepository.get(any(), any()) }
    }

    should("Save any value fetched from USP to DynamoDB") {
        val today = now()
        val tomorrow = today.plusDays(1)

        every { uspRepository.fetch(1) } returns setOf(dummyRestaurantItem(today), dummyRestaurantItem(tomorrow))

        target.get(1, setOf(today))

        verify(exactly = 1) { dynamoRepository.put(setOf(dummyRestaurantItem(today), dummyRestaurantItem(tomorrow))) }
    }

    isolationMode = InstancePerTest

})

private fun dummyRestaurantItem(date: LocalDate) =
    RestaurantItem(1, date, Lunch, 0, "main", "veg", "des", emptyList(), "")