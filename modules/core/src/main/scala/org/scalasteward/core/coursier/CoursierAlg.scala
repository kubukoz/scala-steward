/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core.coursier

import cats.effect._
import cats.implicits._
import cats.Parallel
import coursier.interop.cats._
import org.scalasteward.core.data.Dependency

import scala.concurrent.ExecutionContext

trait CoursierAlg[F[_]] {
  def getArtifactUrl(dependency: Dependency): F[Option[String]]
  def getArtifactIdUrlMapping(dependencies: List[Dependency]): F[Map[String, String]]
}

object CoursierAlg {
  def create[F[_]](
      implicit
      F: Sync[F]
  ): CoursierAlg[F] = {
    implicit val P = Parallel.identity[F]
    implicit val cs: ContextShift[F] = new ContextShift[F] {
      override def shift: F[Unit] = F.unit
      override def evalOn[A](ec: ExecutionContext)(fa: F[A]): F[A] = F.defer(fa)
    }
    val cache = coursier.cache.FileCache[F]()
    val fetch = coursier.Fetch[F](cache)
    new CoursierAlg[F] {
      override def getArtifactUrl(dependency: Dependency): F[Option[String]] = {
        val module = coursier.Module(
          coursier.Organization(dependency.groupId),
          coursier.ModuleName(dependency.artifactIdCross)
        )
        for {
          maybeFetchResult <- fetch
            .addDependencies(
              coursier.Dependency.of(module, dependency.version).withTransitive(false)
            )
            .addArtifactTypes(coursier.Type.pom)
            .ioResult
            .map(Option.apply)
            .recover {
              case _: coursier.error.ResolutionError => None
            }
        } yield {
          maybeFetchResult.flatMap(
            _.resolution.projectCache.get((module, dependency.version)).flatMap {
              case (_, project) =>
                val maybeScmUrl = project.info.scm.flatMap(i => Option(i.url)).filter(_.nonEmpty)
                val maybeHomepage = Option(project.info.homePage).filter(_.nonEmpty)
                maybeScmUrl.orElse(maybeHomepage)
            }
          )
        }
      }

      override def getArtifactIdUrlMapping(dependencies: List[Dependency]): F[Map[String, String]] =
        for {
          entries <- dependencies.traverse(dep => {
            getArtifactUrl(dep).map(dep.artifactId -> _.getOrElse(""))
          })
        } yield Map(entries.filter { case (_, url) => url =!= "" }: _*)
    }
  }
}
