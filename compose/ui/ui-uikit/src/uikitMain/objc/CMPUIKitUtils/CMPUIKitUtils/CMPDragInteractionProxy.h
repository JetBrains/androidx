/*
 * Copyright 2024 The Android Open Source Project
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


#import <UIKit/UIKit.h>
#import "CMPMacros.h"

NS_ASSUME_NONNULL_BEGIN

@interface CMPDragInteractionProxy : NSObject <UIDragInteractionDelegate>

- (NSArray<UIDragItem *> *)itemsForBeginningSession:(id<UIDragSession>)session interaction:(UIDragInteraction *)interaction CMP_ABSTRACT_FUNCTION;

- (BOOL)isSessionRestrictedToDraggingApplication:(id<UIDragSession>)session interaction:(UIDragInteraction *)interaction CMP_ABSTRACT_FUNCTION;

- (BOOL)doesSessionAllowMoveOperation:(id<UIDragSession>)session interaction:(UIDragInteraction *)interaction CMP_ABSTRACT_FUNCTION;

- (UITargetedDragPreview *_Nullable)previewForLiftingItemInSession:(id<UIDragSession>)session item:(UIDragItem *)item interaction:(UIDragInteraction *)interaction CMP_ABSTRACT_FUNCTION;

- (void)sessionDidEndWithOperation:(id<UIDragSession>)session interaction:(UIDragInteraction *)interaction operation:(UIDropOperation)operation CMP_ABSTRACT_FUNCTION;

@end

@interface UIDragItem (CMPInitializers)

+ (instancetype)cmp_itemWithString:(NSString *)string;

+ (instancetype _Nullable)cmp_itemWithObject:(NSObject *)object ofClass:(Class)objectClass;

@end

NS_ASSUME_NONNULL_END
