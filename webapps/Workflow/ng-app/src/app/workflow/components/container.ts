import { Component, ViewEncapsulation } from '@angular/core';

@Component({
    selector: 'workflow-container',
    template: `<router-outlet></router-outlet>`,
    styleUrls: [
        '../styles/form.css'
    ],
    host: {
        '[class.module-container]': 'true'
    },
    encapsulation: ViewEncapsulation.None
})
export class WorkflowContainerComponent { }
