import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { MarkdownConverter } from './markdown-converter';

@Component({
    selector: 'markdown-editor',
    template: `
        <div class="rt-editor">
            <div class="rt-editor__area {{klass}}" [class.edit]="editable" [contentEditable]="editable"
                innerHTML="{{html}}"
                (keyup)="updateValue($event.target)"
                (focus)="focus.emit($event)"
                (blur)="blur.emit($event)">
            </div>
            <!-- <span class="rt-editor__placeholder" *ngIf="!html.length">{{placeHolder}}</span> -->
        </div>
    `,
    providers: [MarkdownConverter]
})

export class MarkdownEditorComponent {
    @Input() editable: boolean = true;
    @Input() markdown: string = '';
    @Input() klass: string = '';
    @Input() placeHolder: string;
    @Input() updateTimeout: number = 150;

    @Output() update = new EventEmitter<any>();
    @Output() focus = new EventEmitter<any>();
    @Output() blur = new EventEmitter<any>();

    private html: string;
    private to: any;

    constructor(private mdc: MarkdownConverter) { }

    ngOnInit() {
        this.html = this.mdc.toHtml(this.markdown);
    }

    updateValue($el) {
        clearTimeout(this.to);
        this.to = setTimeout(() => {
            this.update.emit(this.mdc.toMarkdown($el.innerHTML));
        }, this.updateTimeout);
    }
}
