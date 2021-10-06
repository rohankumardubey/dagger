/*
 * Copyright (C) 2021 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen.xprocessing;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static kotlin.streams.jdk8.StreamsKt.asStream;

import androidx.room.compiler.processing.XHasModifiers;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XTypeElement} helper methods. */
public final class XTypeElements {

  /** Returns {@code true} if the given {@code type} has type parameters. */
  public static boolean hasTypeParameters(XTypeElement type) {
    return XTypes.hasTypeParameters(type.getType());
  }

  /** Returns all non-private, non-static, abstract methods in {@code type}. */
  public static ImmutableList<XMethodElement> getAllUnimplementedMethods(XTypeElement type) {
    return asStream(type.getAllNonPrivateInstanceMethods())
        .filter(XHasModifiers::isAbstract)
        .collect(toImmutableList());
  }

  private XTypeElements() {}
}
