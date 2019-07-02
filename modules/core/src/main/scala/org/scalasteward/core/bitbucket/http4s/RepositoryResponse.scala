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
import org.scalasteward.core.git.Branch
import org.http4s.Uri
import org.scalasteward.core.vcs.data.Repo
import io.circe.Decoder
import cats.implicits._
import org.scalasteward.core.util.uri._
import org.scalasteward.core.vcs.data.UserOut
import io.circe.DecodingFailure

final private[http4s] case class RepositoryResponse(
    name: String,
    mainBranch: Branch,
    owner: UserOut,
    httpsCloneUrl: Uri,
    parent: Option[Repo]
)

private[http4s] object RepositoryResponse {

  implicit private val repoDecoder = Decoder.instance { c =>
    c.as[String].map(_.split('/')).flatMap { parts =>
      parts match {
        case Array(owner, name) => Repo(owner, name).asRight
        case _                  => DecodingFailure("Repo", c.history).asLeft
      }
    }
  }

  implicit val decoder: Decoder[RepositoryResponse] = Decoder.instance { c =>
    for {
      name <- c.downField("name").as[String]
      owner <- c
        .downField("owner")
        .downField("username")
        .as[String]
        .orElse(c.downField("owner").downField("nickname").as[String])
      cloneUrl <- c
        .downField("links")
        .downField("clone")
        .downAt { p =>
          p.asObject
            .flatMap(o => o("name"))
            .flatMap(_.asString)
            .contains("https")
        }
        .downField("href")
        .as[Uri]
      defaultBranch <- c.downField("mainbranch").downField("name").as[Branch]
      maybeParent <- c.downField("parent").downField("full_name").as[Option[Repo]]
    } yield RepositoryResponse(name, defaultBranch, UserOut(owner), cloneUrl, maybeParent)
  }
}
