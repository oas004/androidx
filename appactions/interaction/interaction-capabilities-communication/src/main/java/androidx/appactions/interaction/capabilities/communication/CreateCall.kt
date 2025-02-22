/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.appactions.interaction.capabilities.communication

import androidx.appactions.interaction.capabilities.core.Capability
import androidx.appactions.interaction.capabilities.core.BaseSession
import androidx.appactions.interaction.capabilities.core.CapabilityBuilderBase
import androidx.appactions.interaction.capabilities.core.CapabilityFactory
import androidx.appactions.interaction.capabilities.core.impl.BuilderOf
import androidx.appactions.interaction.capabilities.core.impl.converters.EntityConverter
import androidx.appactions.interaction.capabilities.core.impl.converters.ParamValueConverter
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.CALL_TYPE_SPEC
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.PARTICIPANT_TYPE_SPEC
import androidx.appactions.interaction.capabilities.core.impl.spec.ActionSpecBuilder
import androidx.appactions.interaction.capabilities.core.properties.TypeProperty
import androidx.appactions.interaction.capabilities.core.task.impl.AbstractTaskUpdater
import androidx.appactions.interaction.capabilities.core.values.Call
import androidx.appactions.interaction.capabilities.core.values.Call.CallFormat
import androidx.appactions.interaction.capabilities.core.values.GenericErrorStatus
import androidx.appactions.interaction.capabilities.core.values.properties.Participant
import androidx.appactions.interaction.capabilities.core.values.SuccessStatus
import androidx.appactions.interaction.proto.ParamValue
import androidx.appactions.interaction.protobuf.Struct
import androidx.appactions.interaction.protobuf.Value
import java.util.Optional

private const val CAPABILITY_NAME: String = "actions.intent.CREATE_CALL"

private val ACTION_SPEC =
    ActionSpecBuilder.ofCapabilityNamed(CAPABILITY_NAME)
        .setDescriptor(CreateCall.Property::class.java)
        .setArgument(CreateCall.Argument::class.java, CreateCall.Argument::Builder)
        .setOutput(CreateCall.Output::class.java)
        .bindOptionalParameter(
            "call.callFormat",
            { property -> Optional.ofNullable(property.callFormat) },
            CreateCall.Argument.Builder::setCallFormat,
            TypeConverters.CALL_FORMAT_PARAM_VALUE_CONVERTER,
            TypeConverters.CALL_FORMAT_ENTITY_CONVERTER
        )
        .bindRepeatedParameter(
            "call.participant",
            { property -> Optional.ofNullable(property.participant) },
            CreateCall.Argument.Builder::setParticipantList,
            ParticipantValue.PARAM_VALUE_CONVERTER,
            EntityConverter.of(PARTICIPANT_TYPE_SPEC)
        )
        .bindOptionalOutput(
            "call",
            { output -> Optional.ofNullable(output.call) },
            ParamValueConverter.of(CALL_TYPE_SPEC)::toParamValue
        )
        .bindOptionalOutput(
            "executionStatus",
            { output -> Optional.ofNullable(output.executionStatus) },
            CreateCall.ExecutionStatus::toParamValue
        )
        .build()

@CapabilityFactory(name = CAPABILITY_NAME)
class CreateCall private constructor() {
    class CapabilityBuilder :
        CapabilityBuilderBase<
            CapabilityBuilder, Property, Argument, Output, Confirmation, TaskUpdater, Session
        >(ACTION_SPEC) {
        override fun build(): Capability {
            super.setProperty(Property.Builder().build())
            // TODO(b/268369632): No-op remove empty property builder after Property is removed.
            super.setProperty(Property.Builder().build())
            return super.build()
        }
    }

    // TODO(b/268369632): Remove Property from public capability APIs.
    class Property
    internal constructor(
        val callFormat: TypeProperty<CallFormat>?,
        val participant: TypeProperty<Participant>?
    ) {
        override fun toString(): String {
            return "Property(callFormat=$callFormat, participant=$participant)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Property

            if (callFormat != other.callFormat) return false
            if (participant != other.participant) return false

            return true
        }

        override fun hashCode(): Int {
            var result = callFormat.hashCode()
            result = 31 * result + participant.hashCode()
            return result
        }

        class Builder {
            private var callFormat: TypeProperty<CallFormat>? = null

            private var participant: TypeProperty<Participant>? = null

            fun setCallFormat(callFormat: TypeProperty<CallFormat>): Builder = apply {
                this.callFormat = callFormat
            }

            fun build(): Property = Property(callFormat, participant)
        }
    }

    class Argument
    internal constructor(
        val callFormat: CallFormat?,
        val participantList: List<ParticipantValue>,
    ) {
        override fun toString(): String {
            return "Argument(callFormat=$callFormat, participantList=$participantList)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Argument

            if (callFormat != other.callFormat) return false
            if (participantList != other.participantList) return false

            return true
        }

        override fun hashCode(): Int {
            var result = callFormat.hashCode()
            result = 31 * result + participantList.hashCode()
            return result
        }

        class Builder : BuilderOf<Argument> {
            private var callFormat: CallFormat? = null
            private var participantList: List<ParticipantValue> = mutableListOf()

            fun setCallFormat(callFormat: CallFormat): Builder = apply {
                this.callFormat = callFormat
            }

            fun setParticipantList(participantList: List<ParticipantValue>): Builder = apply {
                this.participantList = participantList
            }

            override fun build(): Argument = Argument(callFormat, participantList)
        }
    }

    class Output internal constructor(val call: Call?, val executionStatus: ExecutionStatus?) {
        override fun toString(): String {
            return "Output(call=$call, executionStatus=$executionStatus)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Output

            if (call != other.call) return false
            if (executionStatus != other.executionStatus) return false

            return true
        }

        override fun hashCode(): Int {
            var result = call.hashCode()
            result = 31 * result + executionStatus.hashCode()
            return result
        }

        class Builder {
            private var call: Call? = null
            private var executionStatus: ExecutionStatus? = null

            fun setCall(call: Call): Builder = apply { this.call = call }

            fun setExecutionStatus(executionStatus: ExecutionStatus): Builder = apply {
                this.executionStatus = executionStatus
            }

            fun build(): Output = Output(call, executionStatus)
        }
    }

    class ExecutionStatus {
        private var successStatus: SuccessStatus? = null
        private var genericErrorStatus: GenericErrorStatus? = null

        constructor(successStatus: SuccessStatus) {
            this.successStatus = successStatus
        }

        constructor(genericErrorStatus: GenericErrorStatus) {
            this.genericErrorStatus = genericErrorStatus
        }

        internal fun toParamValue(): ParamValue {
            var status = ""
            if (successStatus != null) {
                status = successStatus.toString()
            }
            if (genericErrorStatus != null) {
                status = genericErrorStatus.toString()
            }
            val value: Value = Value.newBuilder().setStringValue(status).build()
            return ParamValue.newBuilder()
                .setStructValue(
                    Struct.newBuilder().putFields(TypeConverters.FIELD_NAME_TYPE, value).build()
                )
                .build()
        }
    }

    class Confirmation internal constructor()

    class TaskUpdater internal constructor() : AbstractTaskUpdater()

    sealed interface Session : BaseSession<Argument, Output>
}
