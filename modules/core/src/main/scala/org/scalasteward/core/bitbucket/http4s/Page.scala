/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core.bitbucket.http4s

import io.circe.Decoder

final private[http4s] case class Page[A](values: List[A])

private[http4s] object Page {
  implicit def pageDecoder[A: Decoder]: Decoder[Page[A]] = Decoder.instance { c =>
    c.downField("values").as[List[A]].map(Page(_))
  }
}
