package com.junaldadlawan.event_ticketing_api.tickettemplate.entity;

import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextAlign;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextFieldKey;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * One text field of a ticket template, stored as an element of the template's
 * ordered {@code textFields} collection (list order = drawing order, later on
 * top). {@code x}/{@code y} and {@code fontSize} are percentages of the ticket
 * (x of the width, y and fontSize of the height). See {@link TextFieldKey} for
 * which of the optional columns apply to which key.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketTextField {

    @Enumerated(EnumType.STRING)
    @Column(name = "field_key", nullable = false, length = 20)
    private TextFieldKey key;

    @Column(name = "x", nullable = false)
    private double x;

    @Column(name = "y", nullable = false)
    private double y;

    @Column(name = "font_size", nullable = false)
    private double fontSize;

    @Column(name = "color", nullable = false, length = 7)
    private String color;

    @Column(name = "bold", nullable = false)
    private boolean bold;

    @Enumerated(EnumType.STRING)
    @Column(name = "align", nullable = false, length = 6)
    private TextAlign align;

    @Column(name = "rotation", nullable = false)
    private int rotation;

    /** Dynamic keys only: reserved width, in "X" characters, when there is no sample text. */
    @Column(name = "sample_length")
    private Integer sampleLength;

    /** Dynamic keys only: text whose width is reserved for the value. */
    @Column(name = "sample_text", length = 30)
    private String sampleText;

    /** CUSTOM only. */
    @Column(name = "text", length = 60)
    private String text;

    /** Static keys and CUSTOM only: character positions at which the text starts a new line. */
    @Convert(converter = LineBreaksConverter.class)
    @Column(name = "line_breaks", length = 60)
    private List<Integer> lineBreaks;
}
