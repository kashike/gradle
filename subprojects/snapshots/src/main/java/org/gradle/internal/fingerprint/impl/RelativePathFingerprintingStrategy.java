/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.fingerprint.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interner;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.RelativePathStringTracker;

import java.util.HashSet;
import java.util.Map;

import static org.gradle.internal.fingerprint.impl.EmptyDirectorySensitivity.IGNORE_EMPTY;

/**
 * Fingerprint {@link org.gradle.api.file.FileCollection}s normalizing the path to the relative path in a hierarchy.
 *
 * File names for root directories are ignored. For root files, the file name is used as normalized path.
 */
public class RelativePathFingerprintingStrategy extends AbstractFingerprintingStrategy {
    public static final String IDENTIFIER = "RELATIVE_PATH";
    private final EmptyDirectorySensitivity emptyDirectorySensitivity;

    private final Interner<String> stringInterner;

    public RelativePathFingerprintingStrategy(Interner<String> stringInterner, EmptyDirectorySensitivity emptyDirectorySensitivity) {
        super(IDENTIFIER);
        this.stringInterner = stringInterner;
        this.emptyDirectorySensitivity = emptyDirectorySensitivity;
    }

    @Override
    public String normalizePath(CompleteFileSystemLocationSnapshot snapshot) {
        if (snapshot.getType() == FileType.Directory) {
            return "";
        } else {
            return snapshot.getName();
        }
    }

    private boolean shouldFingerprint(CompleteDirectorySnapshot directorySnapshot) {
        return !(directorySnapshot.getChildren().isEmpty() && emptyDirectorySensitivity == IGNORE_EMPTY);
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> collectFingerprints(Iterable<? extends FileSystemSnapshot> roots) {
        final ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (FileSystemSnapshot root : roots) {
            root.accept(new FileSystemSnapshotVisitor() {
                private final RelativePathStringTracker relativePathStringTracker = new RelativePathStringTracker();

                @Override
                public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                    boolean isRoot = relativePathStringTracker.isRoot();
                    relativePathStringTracker.enter(directorySnapshot);
                    String absolutePath = directorySnapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath) && shouldFingerprint(directorySnapshot)) {
                        FileSystemLocationFingerprint fingerprint = isRoot ? IgnoredPathFileSystemLocationFingerprint.DIRECTORY : new DefaultFileSystemLocationFingerprint(stringInterner.intern(relativePathStringTracker.getRelativePathString()), directorySnapshot);
                        builder.put(absolutePath, fingerprint);
                    }
                    return true;
                }

                @Override
                public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
                    String absolutePath = fileSnapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath)) {
                        FileSystemLocationFingerprint fingerprint = relativePathStringTracker.isRoot() ? new DefaultFileSystemLocationFingerprint(fileSnapshot.getName(), fileSnapshot) : createFingerprint(fileSnapshot);
                        builder.put(absolutePath, fingerprint);
                    }
                }

                private FileSystemLocationFingerprint createFingerprint(CompleteFileSystemLocationSnapshot snapshot) {
                    relativePathStringTracker.enter(snapshot);
                    FileSystemLocationFingerprint fingerprint = new DefaultFileSystemLocationFingerprint(stringInterner.intern(relativePathStringTracker.getRelativePathString()), snapshot);
                    relativePathStringTracker.leave();
                    return fingerprint;
                }

                @Override
                public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                    relativePathStringTracker.leave();
                }
            });
        }
        return builder.build();
    }

    @Override
    public FingerprintHashingStrategy getHashingStrategy() {
        return FingerprintHashingStrategy.SORT;
    }
}
